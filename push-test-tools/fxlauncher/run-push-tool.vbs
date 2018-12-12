Dim fso
Set fso=CreateObject("Scripting.FileSystemObject")
If Not fso.folderExists("pushtool") Then
    fso.CreateFolder("pushtool")
End If
If Not fso.fileExists("pushtool/fxlauncher.jar") Then         
	Set post=CreateObject("Msxml2.XMLHTTP")
	post.Open "GET","http://172.17.149.9:8077/pushtool/fxlauncher.jar"
	'发送请求
	post.Send()
	Set aGet = CreateObject("ADODB.Stream")
	aGet.Mode = 3
	aGet.Type = 1
	aGet.Open()
	'等待3秒，等文件下载
	wscript.sleep 3000 
	aGet.Write(post.responseBody)'写数据
	aGet.SaveToFile "pushtool/fxlauncher.jar",2
End If

set ws=WScript.CreateObject("WScript.Shell")
ws.currentdirectory = "pushtool"
ws.Run "cmd /c java -Xbootclasspath/p:alpn-boot.jar -jar fxlauncher.jar --uri=http://172.17.149.9:8077/pushtool",vbhide