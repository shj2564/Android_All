# SoNG PC PowerOff

외부에서 VPS를 통해 SoNG-HoME Windows 서비스를 정상 종료하는 전용 Android 앱입니다.

- 기존 WOL 기능 변경 없음
- HTTPS 전용
- VPS 자체서명 인증서를 앱에 고정
- 앱 인증키는 소스/저장소에 포함하지 않음
- 최초 실행 시 64자리 인증키를 1회 입력
- PC 온라인 상태 확인
- 종료 전 확인창 표시
- Windows 측 60초 유예 후 정상 종료

API: `https://52.78.116.196/__songpower/`
