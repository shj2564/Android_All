# CMZX Car Infos 원본 분석

확인일: 2026-09-11. 대상: 사용자가 차량에서 추출해 첨부한 원본 APK.

## 결론과 다음 파일

다음에 확보할 패키지는 **`com.reglink.services`**다. 현재 APK의 `BaseActivity.bindDroidService()`가 이 패키지를 명시적으로 지정하고 차량 서비스에 연결한다. 단순히 비슷한 앱 이름을 추측한 결과가 아니다.

ML Manager의 시스템 앱 목록에서 `services`를 검색한 뒤, 패키지명이 정확히 `com.reglink.services`인 항목을 Extract로 추출해 전달하면 된다. 검색 결과가 없으면 `reglink` 검색 결과 또는 시스템 앱 목록에서 패키지명을 확인한다. 앱의 화면 표시 이름은 아직 확인하지 못했다.

## 원본 식별

| 항목 | 값 |
|---|---|
| 파일 | com.reglink.vehicleinfo_20260428.apk |
| 크기 | 3,590,841 bytes |
| 패키지 | com.reglink.vehicleinfo |
| 앱 이름 | Car Infos |
| versionName | 20260428 |
| versionCode | 20260429 |
| minSdk / targetSdk | 17 / 20 |
| sharedUserId | android.uid.system |
| DEX | classes.dex 1개, 클래스 3,488개 |
| assets / native lib | 해당 APK에 assets/ 및 lib/ 항목 없음 |

SHA-256:

```text
3482627cb4d8155b4a376fdbccdc475f87774cba148fd1358632843f4b751e05
```

추출한 서명 인증서 SHA-256:

```text
c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8
```

인증서 Subject에 Android / android@android.com이 표시된다. 인증서의 식별값이며 서명 검증 도구를 통한 전체 APK 서명 검증 완료를 의미하지 않는다. 앱의 targetSdk/minSdk는 기기의 실제 Android 버전을 의미하지 않는다.

## 코드에서 확인한 서비스 연결

`com.reglink.common.ServiceManager.getDroidService()`는 Android 서비스 관리자에서 이름이 `reglink.droid`인 Binder를 조회한다.

`BaseActivity.bindDroidService()`는 해당 Binder를 얻지 못했을 때 아래 지정으로 바인딩한다.

```text
Intent action: com.reglink.action.DroidCarService
Intent package: com.reglink.services
```

`com.reglink.vehicleinfo.Communication.initialize()`는 DroidService에서 이름이 `CANBox`인 서비스를 가져와 `ICANBoxService` 인터페이스로 연결하고 수신 콜백을 등록한다.

`Communication.send(type, data...)`는 전달받은 값을 CANBoxPacket으로 만들고, `sendPkt()`는 `ICANBoxService.write(packet)`을 호출한다.

`ICANBoxService`에는 다음 인터페이스가 선언돼 있다.

- 수신·조회: registerCallback, unregisterCallback, getLastCANBoxPkt, getHistoryData, isConnected, getCANBoxType, getDecoderName
- 전송·변환: write, encodePkt, rawWrite
- 상태 변경: suspend, resume

인터페이스 선언과 클라이언트 호출 경로가 확인된 것이다. 별도 일반 앱이 실제 기기에서 접근 가능한지, 권한 검사가 무엇인지, Raise 패킷을 어떤 방식으로 인코딩하는지는 서비스 구현을 확인해야 한다.

`ICarService`에는 isReversing, getGear, getSpeed 등의 조회 인터페이스도 있다. 실제로 이 기기에서 반환되는 값은 아직 읽지 않았다.

## 미러 메뉴 확인 결과

리소스 문자열, DEX 문자열, 액티비티 선언, 관련 클래스를 조사했다. 현재 파일에서 Peugeot/PSA 후진 미러 자동 하향 메뉴와 그 기능을 지정하는 명령은 찾지 못했다. DEX의 mirror 관련 일부 문자열은 이미지 좌우 반전이나 카메라·라이브러리 용도이므로 미러 모터 하향 기능으로 취급하지 않았다.

`VehicleSettingsActivity`와 `VehicleInfoActivity`의 onCreate는 상위 BaseActivity.onCreate를 호출하고 끝난다. 해당 클래스에는 미러 설정 컨트롤이나 자체 화면 구성 코드가 없다. BaseActivity의 공통 서비스 바인딩도 함께 확인했다. 따라서 Activity Launcher로 이 이름의 화면을 여는 것만으로 미러 메뉴가 나타난다고 안내할 근거는 없다.

이 APK의 CANBoxHandlerFactory.createRaiseHandler에는 volkswagen, jeep, honda, toyota, nissan, mazda 분기가 확인된다. Peugeot/PSA 전용 분기는 확인되지 않았다. **이것은 이 APK 내부의 구현 범위에 관한 결과이며, 기기 전체에 Peugeot 지원이 없다는 뜻은 아니다.** 실제로 기존 사진에는 5008 프로필과 차량 설정이 표시돼 있으므로, 다른 앱·서비스·차종 정의 자료의 역할을 계속 확인해야 한다.

## 차종 정의·업데이트 연결 단서

ModelDbHelper는 아래 콘텐츠 제공자에서 차종 데이터를 조회한다.

```text
content://com.reglink.services.provider.car/models
content://com.reglink.services.provider.car/prefer_model
```

ModelInfo/ModelData 등에는 script 관련 필드가 있고, ScriptUpdate는 Message 서비스로 refresh_script 요청을 보내며 다운로드·로드 상태를 받는 구조다. 차종별 정의나 동작 중 일부가 이 APK 밖에 존재할 가능성을 뒷받침한다. 실제 스크립트 파일의 위치·내용은 아직 확보하지 않았다.

ServiceUpdate 코드에는 services5.apk 다운로드 주소와 `com.reglink.services`의 설치 버전을 비교하는 로직이 있다. 해당 주소에 지금 어떤 파일이 배포되는지는 이번 분석에서 조회하지 않았다. 온라인 업데이트 실행·설치·재부팅을 요청하거나 수행하지 않았다.

## DUDU 성공 사례와의 비교

| 항목 | DUDU 성공 사례 APK | 현재 CMZX Car Infos APK |
|---|---|---|
| 화면 패키지 | com.syu.canbus | com.reglink.vehicleinfo |
| 관련 서비스 | com.syu.ms | com.reglink.services 명시적 연결 확인 |
| 통신 형태 | DataCanbus.PROXY의 명령 호출 | reglink.droid → CANBox → ICANBoxService.write |
| 미러 설정 | 명령 88, 설정 번호 30, 상태 276 확인 | 현재 APK에서 대응 기능 미확인 |
| 다음 확인점 | DUDU 구현은 이전 분석으로 확보 | 실제 서비스 구현·차종 정의·호출 권한 |

DUDU의 명령 번호나 MCU 전달 배열을 Reglink의 API에 그대로 대응시킬 근거는 아직 없다. 두 플랫폼에서 같은 기능을 지정하는 패킷 종류·데이터·수신 상태가 어떻게 연결되는지 서비스 APK로 대조해야 한다.

원본에서 발견한 CAN 통신 인터페이스 덕분에 독립 시험 앱의 검토가 구체화됐다. 다만 현 단계에서 CMZX용 실차 제어 APK를 만들거나 실행 검증한 것은 아니다. 차량의 FOCAL·CAN 프로필·설정 데이터는 변경하지 않았다.

## 분석 방법과 범위

APK ZIP 구조·매니페스트·리소스·DEX를 Androguard로 정적으로 읽고, 관련 메서드의 바이트코드와 디컴파일 출력을 대조했다. 실제 차량에 명령을 보내지 않았다. APK 압축 CRC와 해시를 확인했다.

동봉한 selected/의 Java 파일은 이해를 돕는 디컴파일 결과다. 일부 타입과 제어 흐름 표현은 디컴파일러의 복원 한계가 있으므로, 그대로 재컴파일할 개발 원본 소스로 취급하지 않는다. 주요 호출 경로는 disasm 파일과 함께 확인할 수 있다.

분석 자료의 출처는 사용자가 직접 제공한 위 APK다. 과거 DUDU 성공 사례 근거는 이전에 전달한 Peugeot-5008-mirror-research.md 및 개발자 원문에 기록돼 있다.