# Install e2studio Bridge

## 1. 빌드

```powershell
cd D:\workspace_ai\e2studio-mcp-agent
mvn -f eclipse-bridge/releng/pom.xml verify
```

성공 시 bundle 산출물은 일반적으로 `eclipse-bridge/bundles/com.example.e2studio.agent.bridge/target/` 아래에 생성된다.

## 2. Dropins 설치

1. e2studio를 종료한다.
2. 생성된 bundle JAR을 `C:\Renesas\e2_studio\eclipse\dropins\` 로 복사한다.
3. e2studio를 다시 실행한다.

## 3. 초기 기동 확인

e2studio가 workbench까지 완전히 뜨면 bridge가 자동 시작된다.

토큰 파일 확인:

```text
C:\Users\<user>\workspace_e2studio\.metadata\.plugins\com.example.e2studio.agent.bridge\token
```

포트 기본값:

```text
39232
```

변경하려면 e2studio 실행 전에 환경변수 설정:

```powershell
$env:E2STUDIO_AGENT_PORT="39241"
```

## 4. HTTP 확인

```powershell
$token = (Get-Content "C:\Users\<user>\workspace_e2studio\.metadata\.plugins\com.example.e2studio.agent.bridge\token" -Raw).Trim()
$headers = @{ Authorization = "Bearer $token" }
Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:39232/health" -Headers $headers
```

## 5. Update Site 방식

현재 releng 구조는 feature/repository packaging을 포함하지만, README 기준 운영 절차는 dropins를 우선한다. p2 update site packaging은 후속 검증이 더 필요하며, 관련 불확실성은 [docs/OPEN_QUESTIONS.md](/D:/workspace_ai/e2studio-mcp-agent/docs/OPEN_QUESTIONS.md)에 기록했다.
