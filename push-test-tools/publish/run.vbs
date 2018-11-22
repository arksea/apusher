set ws=WScript.CreateObject("WScript.Shell")
ws.Run "cmd /c .\jre\bin\java.exe -Xbootclasspath/p:lib/alpn-boot-8.1.11.v20170118.jar -jar push-test-tools-1.2.0-SNAPSHOT.jar",vbhide