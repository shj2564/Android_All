# Android_All

2019 Peugeot 5008 GT + CMZX Android All-in-One 연구/복구 전용 저장소.

## 범위
- 전방 카메라 R → D 전환 복구
- 계기판 PERSONAL 좌/우 표시 설정 복구
- 후진 미러 하향 복구
- 운전석/조수석 마사지 기능 복구
- CMZX / ReglinkService / Raise PSA CAN 정적·동적 분석

## 기준 장치
- Head unit: CMZXR62N-U1
- Android: 8.1 / API 27
- SoC: mt6765 / mtk6762
- MCU: 95.08@260715
- CAN box: raise / decoder raise-psa
- Vehicle profile: psa_5008_2014_2019
- ReglinkService: 260129-1424 / 26012914

이 저장소는 기존 shj2564/PeugeotBackup에 섞여 있던 Android All-in-One 관련 작업을 분리해 이전한 기준 저장소다.
원격시동/백업 서버 작업은 이 저장소의 범위가 아니다.

## 구조
- frontcamera / tools/frontcam-v18: 현재 전방카메라 계보
- clustercheck: 계기판 진단/복구
- massagecheck: 마사지 복구
- mirrorcheck: 후진 미러 하향 성공본/소스/분석
- docs: ReglinkService/CAN 및 기능 분석
- history/legacy-branches: 기존 PeugeotBackup의 관련 브랜치별 추가 파일 스냅샷

신규 Android All-in-One 작업의 source of truth는 이 저장소다.
