# CMZX 5008 계기판 PERSONAL 설정 진단 0.1

대상: 2019 Peugeot 5008 GT / CMZXR62N-U1 / ReglinkService 260129-1424 / Raise PSA-RZ-15.

- 독립 패키지: `kr.song.peugeot.clustercheck`
- 앱 실행만으로 차량 설정을 송신하지 않음
- 서비스 해시/버전, Android API/하드웨어, 차종, MCU, Raise CANBox/decoder/버전 검증
- 고정 송신 형식: `CANBoxPacket(type=0x8C, data=[LEFT, RIGHT])`
- LEFT/RIGHT 각각 0~8만 허용
- 사용자가 확인한 1회만 송신, 자동 재전송 없음
- 자동 원복 없음: 현재 좌/우 값의 신뢰 가능한 수신 경로가 아직 확인되지 않았기 때문
- rawWrite, CAN 프로필 변경, FOCAL/미러/카메라 설정 변경 기능 없음
