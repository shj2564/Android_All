# Peugeot 5008 후진 미러 하향 조사 — 성공 사례 원본 확보

확인일: 2026-09-11. 차량에 연결하지 않고, 기존 사진·공개 성공 사례·배포 APK를 분석했다.

## 이번에 확인한 결론

성공 사례 개발자가 배포한 APK 두 개를 실제로 내려받았다. 미러 하향 메뉴의 표시뿐 아니라 설정 명령과 응답 상태 처리까지 코드에서 확인했다. 따라서 CMZX용 기능 구현을 검토할 구체적인 참고 자료가 확보됐다.

확인된 성공 차량은 **2017 Peugeot 3008 + DUDU/FYT + Raise PSA-RZ-15**다. 사용자의 **2019 Peugeot 5008 GT + CMZX65-U1**에서 이 APK가 동작한다는 뜻은 아니다. 이번 결과에는 CMZX 설치 시험, 차량 통신 시험, 실차 미러 동작 시험이 포함되지 않는다.

## 사용자의 기존 구성

기존 사진에서 읽은 정보를 유지한다.

| 항목 | 확인된 값 |
|---|---|
| 차량 | 2019 Peugeot 5008 GT, 순정 FOCAL 외장 앰프 |
| 헤드유닛 | CMZX65-U1 |
| 빌드 | CMZXR62N-U1_R6210_S5.34_2026-05-25 10:03:27 |
| CPU·메모리 표기 | A53 8코어 2.0GHz, RAM 4GB / 저장공간 64GB |
| Android 표기 | 15. 실제 SDK/API 수준은 아직 확인하지 못함 |
| CAN 문자열 | PSA-RZ-15-0128.212.06-HSE |
| 현재 차종 프로필 | Peugeot 2014~2019 5008-General에 해당하는 화면 표기 |

`Set Item Status` 사진에는 전동 트렁크, 자동 트렁크 열림, 경고 메시지 스위치만 보인다. 보이지 않는 영역이나 미확인 화면에 미러 기능이 없다고 단정하지 않는다. 차량 설정 앱의 패키지명·서비스명·서명은 사진에서 확인되지 않았다.

현재 CAN 프로필과 FOCAL 관련 설정을 보존하는 방향으로 조사한다. 판매자의 기술팀 전달 답변은 AI 챗봇 표시가 있었으므로, 엔지니어의 호환성 확답이나 패치 제공으로 취급하지 않는다.

## 성공 사례와 원본 파일

개발자는 2025-08-15 APK 두 개를 제공했다. 같은 날 요청자가 설치 후 기능이 정상 작동한다고 보고했다. 메뉴 경로는 `Vehicle Information > Function Status > Other Settings`다. [DUDUAUTO 원문](https://forum.dudu-auto.com/d/2051-missing-function-auto-tilt-down-of-side-mirror-when-reversing)

사례의 시스템 사진에는 `RZC(Raise)-Peugeot/Citroen-4008-2017(65875)-PSA-RZ-15-0128.212.06-HSE`가 표시된다. 사용자의 CAN 버전 문자열과 일치한다. 다만 사례 기기는 UIS7870/FYT이며 사용자의 CMZX/R6210과 기기 플랫폼이 다르다. 문자열 일치로 CAN 하드웨어 개정이나 헤드유닛 API까지 동일하다고 판단할 수 없다. [사례 시스템 사진](https://forum.dudu-auto.com/assets/files/2025-08-11/1754952181-961297-20250811-224329.jpg)

배포 폴더의 파일들은 2026-09-11 현재 접근 및 다운로드가 가능했다. 두 파일의 폴더 생성·업로드 날짜는 개발자 게시일과 일치한다. 당시 파일과의 동일성을 입증하는 개발자의 별도 해시는 없으므로, 아래 해시는 이번에 내려받은 바이트의 식별값이다. [개발자 배포 폴더](https://drive.google.com/drive/folders/1mLCFIKZuwPBRwsf4g9oexFXLSMKqK6I0)

| 원래 파일명 | 바이트 수 | 내부 패키지 | 앱 이름 |
|---|---:|---|---|
| 190000000_com.syu.canbus.apk.1 | 86,253,529 | com.syu.canbus | Vehicle settings |
| 190000000_com.syu.ms.apk.1 | 5,748,798 | com.syu.ms | MainServer |

원본 파일명과 바이트를 그대로 보존했다. `.apk.1`은 배포자가 붙인 확장자이며, 내부에는 AndroidManifest.xml, classes.dex, resources.arsc가 있는 APK 구조가 확인된다. 펌웨어 이미지나 공개 개발 소스 프로젝트가 아니다.

- [차량 설정 APK 원본](https://drive.google.com/file/d/1PkB06FNjwlCXtBdtTQ9nuAAsN-uayNqe/view)
- [MainServer APK 원본](https://drive.google.com/file/d/1ORcqIvOqFuwBja0V6yx-2wJpNAb698iG/view)

SHA-256:

```text
c7f8d34beca868c23e3a636606a029d2f21f575f37abf2c45c9f5c51240cde03  190000000_com.syu.canbus.apk.1
7d73ffc7eaf209df35b265f65191851511c4e63ce1b9f514c1b973bba2cc7992  190000000_com.syu.ms.apk.1
```

## APK 내부에서 확인한 내용

다음은 확보한 APK의 매니페스트, 리소스, DEX를 직접 읽은 정적 분석 결과다. 다른 제조사 기기에서 실행해 확인한 결과는 아니다.

| 항목 | Vehicle settings | MainServer |
|---|---|---|
| versionName / versionCode | 1.0 / 2125081510 | 1.0 / 2125081510 |
| minSdk / targetSdk | 23 / 23 | 23 / 23 |
| sharedUserId | android.uid.systemui | android.uid.system |
| 주요 서비스 | com.syu.canbus.ServiceKeepAlive | app.ModuleService, app.ToolkitService |

두 APK에서 추출한 서명 인증서 SHA-256은 `bbc7740a0713c7a43b343ea4f9423c3b861139c9d39c491bcff3572acf0b1997`로 같고, 인증서의 조직 이름은 LSEC다. 이는 추출한 인증서의 식별 결과다. CMZX의 시스템 서명과 같다는 뜻이 아니며, 별도의 APK 서명 검증기 실행이나 악성코드 검사를 완료했다는 뜻도 아니다.

MainServer 매니페스트에는 CAN 외에도 라디오, 블루투스, 사운드, 앰프 등의 서비스 액션이 들어 있다. 그래서 이 파일 전체를 CMZX에 설치하거나 기존 서비스를 대체하는 방식은 FOCAL을 보존하는 시험 방법으로 제시할 수 없다. Android의 공유 UID는 해당 공유 UID에 속한 앱들과의 인증서 일치도 요구한다. [Android 매니페스트 문서](https://developer.android.com/guide/topics/manifest/manifest-element?hl=en)

또한 표준 Android 15에서는 targetSdk가 24 미만인 앱의 신규 설치가 제한된다. 이 두 APK는 23이다. 사용자의 기기에는 Android 15라고 표시되지만 실제 API 수준과 제조사 변경 사항은 미확인이라, 구체적인 설치 실패 여부까지 예측하지 않는다. [Android 15 설치 제한](https://developer.android.com/about/versions/15/behavior-changes-all#minimum-target-api-level)

### 미러 기능의 코드 경로

1. 리소스 `klc_comfort_Mirror_reversing_automatic_tilt_str`의 ID는 `0x7f0903f6`이다. 영어 문구는 `Mirror reversing automatic tilt`, 한국어 리소스는 `후면 뷰 미러 리버스 틸트`다.
2. `layout_rzc_biaozhi408_func_others.xml`에서 이 문구와 연결된 체크 컨트롤은 `ctv_checkedtext8` (`0x7f0b0107`)이다.
3. `RZC_BZ408FuncOthersActi`는 해당 컨트롤에 내부 클래스 `$31`의 클릭 처리를 연결한다.
4. 클릭 처리는 `DataCanbus.DATA[276]`을 읽어 0과 1을 토글한 뒤 `DataCanbus.PROXY.cmd(88, [30, 새 값], null, null)`을 호출한다.
5. MainServer의 `CAR_0339_RZC4_PSA_ALL.cmd()`에서 명령 88은 `carSet(설정 번호, 값)`으로 전달된다.
6. `carSet()`은 `ToolkitDev.writeMcu()`로 정수 배열 `[227, 5, -128, 설정 번호, 값]`을 전달한다. 미러 설정 번호는 30이다.
7. 같은 드라이버의 수신 처리에서 메시지 종류 `0x38`, 파서 기준 `buffer[offset + 6]`의 bit 3을 상태 276으로 전달한다. UI의 알림 처리도 이 상태로 체크 표시를 갱신한다.

따라서 이것은 버튼을 눌러 표시만 바꾸는 메뉴가 아니라, 설정 전송과 수신 상태 반영이 구현된 메뉴다. 명령은 미러 모터의 각도를 직접 지정하는 형태가 아니라 자동 하향 기능의 설정을 토글하는 형태다. 순정 기능의 실제 작동은 차량 측에서 담당한다는 해석과 부합하지만, 차량 ECU 내부 동작은 이번 APK 분석의 범위 밖이다.

**위 배열은 DUDU/FYT 내부의 MCU 전달 인자다. 차량 CAN 프레임·CAN ID·CMZX의 직렬 명령으로 바로 사용하면 안 된다.** 아래층의 프레이밍, 통신 경로, CMZX API는 별도로 확인해야 한다. 같은 화면에 있는 다른 미러 관련 상태 194/명령 85와도 구분했다.

## 데모 가능 범위

| 방식 | 현재 판단 |
|---|---|
| 설정 켜기·끄기와 R/P 전환을 보여 주는 화면 데모 | 제작 가능. 이번 대화에 차량 미연결 시뮬레이션 제공 |
| 독립된 Android 앱으로 화면만 시연 | 개발 가능. 이번에 설치형 APK를 제작하거나 CMZX에서 실행 검증한 것은 아님 |
| 성공 사례 APK를 DUDU에서 적용 | 원문 사용자 성공 보고 있음. 기기·빌드·서명 등의 호환 조건 확인 필요 |
| 성공 사례 APK를 CMZX에 그대로 설치 | 호환성 미확인. 시스템 서비스·서명·SDK 의존성 때문에 설치 지침으로 제공하지 않음 |
| CMZX에서 실제 미러를 움직이는 시험 앱 | CMZX의 기존 차량 설정/CAN 서비스 APK와 호출 경로를 확인한 후 구현 가능성을 판정할 수 있음 |

화면 데모는 동작 개념을 보여 주기 위한 것이다. 기어 입력은 사용자가 선택하는 가상 입력이고, 각도·화면 이동량·애니메이션 시간은 차량 측정값이 아니다. 차량 통신, APK 설치, 기기 설정 변경, 네트워크 전송을 수행하지 않는다.

## 실차 데모로 이어가기 위한 다음 자료

필요한 새 자료는 사진 반복 제출이 아니라 **현재 CMZX에 설치된 차량 설정 앱과 CAN 연동 서비스의 원본 APK**다. 가능하면 패키지명, 버전, 시스템 SDK/API 수준도 함께 확보하면 좋다. 이름만 비슷한 인터넷 APK를 대신 사용하지 않는다.

이 자료에서 확인할 것은 현재 프로필에 해당하는 Raise PSA 드라이버, 미러 설정 지원 여부, 앱에서 서비스로 연결하는 방식, 서명/권한 요구다. 같은 설정이 이미 있으면 지원되는 진입 경로를 찾고, 구현이 빠져 있으면 제조사 패치 또는 기존 서비스와 통신하는 독립 시험 앱의 가능성을 검토한다. 현재 FOCAL 구성과 5008 프로필은 유지한다.

기존 사진만으로는 이 패키지 정보와 원본 코드까지 복원할 수 없다. 노트북 없이 할 수 있는 APK 내보내기 경로를 확인하는 것이 다음 수집 단계다. LTE 연결만으로 ADB가 활성화되는 것은 아니다.

## 보관 자료와 검증 범위

배포 APK 원본 2개, SHA-256 목록, 이 조사서, 매니페스트 2개, 미러 관련 리소스 및 DEX 발췌, 개발자가 게시한 시스템·메뉴 사진을 함께 보관한다. 내부 분석 스크립트는 APK를 실행하지 않고 ZIP·XML·DEX를 읽었다. 압축 파일의 CRC와 원본 APK의 SHA-256을 확인한다. 차량 작동, CMZX 설치, 앱 실행 호환성은 미검증이다.

공식 2019 5008 설명서의 브라질판에는 후진 시 미러 하향 및 주행/컴포트 설정에서의 활성화가 설명돼 있다. 국가별 메뉴 번역·차량 옵션 차이는 고려해야 한다. [Peugeot 공식 2019 설명서, 인쇄 89쪽](https://carros.peugeot.com.br/content/dam/peugeot/brazil/b2c/owners/maintain-your-car/manuais/5008/5008-br-manual-capa-garantia-ed19-19.pdf)