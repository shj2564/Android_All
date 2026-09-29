# CMZX / Peugeot 5008 계기판 PERSONAL 설정 정적분석

분석일: 2026-09-27 (KST)
대상: 2019 Peugeot 5008 GT / CMZXR62N-U1 / Raise PSA-RZ-15

## 결론

현재 CMZX의 `raise-psa` / `psa_5008_2014_2019` 메뉴 스크립트에는 순정의 계기판 PERSONAL 좌/우 표시 설정 UI가 구현되어 있지 않다. 그러나 같은 PSA Raise/RZC4 계열의 FYT/DUDU 구현에는 이 기능이 실제 송신 코드까지 존재한다.

DUDU/FYT 원본 APK의 정확한 명령 경로는 다음과 같다.

`RZC_BZ408FuncOthersActi` → `PROXY.cmd(82, [left, right])` → MainServer `CAR_0339_RZC4_PSA_ALL.cmd(82)` → `Panel_Set(left,right)` → `ToolkitDev.writeMcu([0xE3,0x05,0x8C,left,right])`

DUDU MainServer의 `cmd()` sparse-switch payload를 직접 해석한 결과 key `82`의 타깃은 code-unit `0x0182`이며, 그 위치에서 정확히 `Panel_Set(args[0], args[1])`을 호출한다.

`Panel_Set(II)`는 좌/우 값을 각각 상태 186/187에 즉시 반영하고 SharedPreferences에 저장한 뒤 다음 5바이트 MCU 인자를 전송한다.

```
E3 05 8C LL RR
```

`LL`과 `RR`은 각각 0~8이다.

## CMZX Raise-Psa 직렬 프레임으로의 대응

현재 CMZX `RaisePsaDecoder.encode(CANBoxPacket)`는 데이터 종류와 payload를 제한하지 않고 다음과 같이 인코딩한다.

```
FD (payload_length+3) TYPE PAYLOAD... CHECKSUM
```

따라서 DUDU의 계기판 설정 MCU 인자에서 FYT 접두어 `E3`를 제외하면 CMZX/Reglink에서 대응되는 패킷은:

```
CANBoxPacket(type=0x8C, data=[LL, RR])
```

직렬 프레임은:

```
FD 05 8C LL RR CS
CS = (05 + 8C + LL + RR) & FF
```

예:
- LL=0, RR=0 → `FD 05 8C 00 00 91`
- LL=8, RR=8 → `FD 05 8C 08 08 A1`

`CANBoxService.write()`는 패킷 타입별 허용 목록 없이 현재 decoder의 `encode()`를 호출해 활성 상태에서 통신 서비스로 전달한다. 따라서 서비스 코드상 `type=0x8C` 자체가 차단되는 구조는 확인되지 않았다.

## 현재 CMZX 스크립트의 누락 증거

실차가 보고한 묶음 `26090708`의 암호화 스크립트를 `com.reglink.services`의 실제 JNI 키 `REGLINKSCRIPTKEY`로 AES-128-ECB 복호화했다. 그 안의 `model_raise_psa.js`는 139,118바이트이고 SHA-256은:

`d5d7302e5d4eef766de713240f211647c0195b20bd46d84d54b2561ad2a5ae5d`

으로 기존 26082601/26091003 분석과 동일하다.

이 Raise 스크립트에는 `lang_146/147`, instrument/cluster/dashboard/panel에 해당하는 좌/우 PERSONAL 설정 UI가 없다. 5008 모델에서 생성되는 탭도 기존 확인과 동일한 9개뿐이다.

반면 같은 26090708의 공통 영어 언어 리소스에는 정확히 다음 문자열이 존재한다.

- `lang_146`: Left Gauge Display
- `lang_147`: Right Gauge Display
- `lang_148`: Engine Info
- `lang_149`: Accelerometer
- `lang_150`: Navigation
- `lang_151`: Tachometer
- `lang_152`: Trip Computer
- `lang_153`: Media
- `lang_165`: Mirror Adj. in Reverse
- `lang_168`: Gauge Display Custom

즉 공통 UI 언어 자원에는 기능 개념이 있으나 Raise 5008 모델 스크립트가 이를 화면에 연결하지 않은 상태다.

## 다른 PSA 디코더와의 교차검증

같은 Reglink 묶음의 `model_hiworld_psa.js`에는 실제로 좌/우 계기판 PERSONAL 메뉴가 구현되어 있다.

좌측은 0~8 값을 `[0x7D,0x11,value]`, 우측은 `[0x7D,0x12,value]`로 전송하며 선택 항목은 None, Driving Assistance, Engine Info, Accelerometer, Temperature, Navigation, Tachometer, Trip Computer, Media다.

하지만 이 `0x7D` 프레임은 Hiworld용이며 Raise에 그대로 사용하면 안 된다. 동일 기능의 제조사별 명령이 다르다는 사실은 이미 다음 사례로 확인된다.

- Hiworld 미러 후진조정: `[0x7D,0x23,value]`
- Raise/RZC4 미러 후진조정: `[0x80,0x1E,value]`

따라서 이번 계기판 복구 후보는 Hiworld `7D 11/12`가 아니라 DUDU/FYT Raise/RZC4에서 직접 확인한 `type 0x8C + [left,right]` 경로를 사용해야 한다.

## DUDU/FYT 원본 검증

개발자 배포 원본을 다시 받아 SHA-256을 확인했다.

- `190000000_com.syu.canbus.apk.1` 86,253,529 bytes
  - SHA-256 `c7f8d34beca868c23e3a636606a029d2f21f575f37abf2c45c9f5c51240cde03`
- `190000000_com.syu.ms.apk.1` 5,748,798 bytes
  - SHA-256 `7d73ffc7eaf209df35b265f65191851511c4e63ce1b9f514c1b973bba2cc7992`

MainServer 원본 APK의 `classes.dex`를 직접 디스어셈블하여 `CAR_0339_RZC4_PSA_ALL`의 `cmd()`와 `Panel_Set()`을 확인했다.

## 중요한 한계

1. DUDU의 상태 186/187은 `Panel_Set()` 내부에서 명령 직후 로컬 값으로 갱신된다. 이 값만으로 차량 ECU가 설정을 수락했다는 ACK로 볼 수 없다.
2. `FD 05 8C LL RR CS`는 코드 경로를 교차대조하여 도출한 Raise 본체-CANBOX 직렬 프레임이다. 차량 CAN 중재 ID/차량 CAN 데이터 프레임 자체가 아니다.
3. SONG 차량에서 `type=0x8C`를 실제 송신하여 계기판 PERSONAL 내용이 변경되는지는 아직 실차 미검증이다.
4. 따라서 현재 정적분석으로 확정 가능한 것은 “명령 경로와 포맷이 존재하며 CMZX 서비스가 이를 인코딩/전달할 수 있는 구조”까지다. 실제 ECU 수락은 별도의 1회 제한 실차 시험으로 확인해야 한다.

## 다음 실차 시험 후보

기존 미러 성공 APK와 같은 안전 구조로 별도 진단 APK를 만들 경우:

- 현재 기기/서비스/CAN 조합을 먼저 검증
- 임의 입력 금지
- `type=0x8C`, payload `[LL,RR]`만 허용
- LL/RR 각각 0~8만 허용
- 사용자가 명시적으로 누른 1회만 송신
- 원래 값 복원 버튼 제공
- 송신 전후 callback/cache를 기록하되 ACK로 표시하지 않음
- 계기판 PERSONAL 모드를 사용자가 직접 확인하여 실제 적용 여부 판정

이 방식은 기존 `raise-psa` 프로필, FOCAL 오디오, 미러 설정, 전방 카메라 설정을 변경하지 않고 계기판 설정 패킷만 시험할 수 있다.