#ifndef AppVersion
  #define AppVersion "2.0.0"
#endif

[Setup]
AppId={{8E9A7C2F-1F53-4D0A-9B3C-6F4A1E92D510}
AppName=GPT Remote PC
AppVersion={#AppVersion}
VersionInfoVersion={#AppVersion}.0
AppPublisher=GPT Remote
DefaultDirName={autopf}\GPT Remote PC
DefaultGroupName=GPT Remote PC
OutputDir=..\dist
OutputBaseFilename=GptRemotePC-Setup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
CloseApplications=force
RestartApplications=no
UninstallDisplayIcon={app}\GptRemotePC.exe

[Tasks]
Name: "desktopicon"; Description: "Create a &Desktop shortcut"; GroupDescription: "Additional shortcuts:"; Flags: checkedonce
Name: "autostart"; Description: "Start GPT Remote PC automatically in System Tray on Windows login"; GroupDescription: "Startup:"; Flags: unchecked

[Files]
Source: "..\dist\GptRemotePC.exe"; DestDir: "{app}"; Flags: ignoreversion restartreplace

[Icons]
Name: "{group}\GPT Remote PC"; Filename: "{app}\GptRemotePC.exe"
Name: "{group}\Uninstall GPT Remote PC"; Filename: "{uninstallexe}"
Name: "{autodesktop}\GPT Remote PC"; Filename: "{app}\GptRemotePC.exe"; Tasks: desktopicon
Name: "{userstartup}\GptRemotePC"; Filename: "{app}\GptRemotePC.exe"; Parameters: "--tray"; Tasks: autostart

[Run]
Filename: "netsh"; Parameters: "advfirewall firewall delete rule name=""gptremotepc.exe"""; Flags: runhidden waituntilterminated
Filename: "netsh"; Parameters: "advfirewall firewall delete rule name=""GPT Remote PC TCP 58765"""; Flags: runhidden waituntilterminated
Filename: "netsh"; Parameters: "advfirewall firewall delete rule name=""GPT Remote PC UDP 58766"""; Flags: runhidden waituntilterminated
Filename: "netsh"; Parameters: "advfirewall firewall delete rule name=""GPT Remote PC Screen 58767"""; Flags: runhidden waituntilterminated
Filename: "netsh"; Parameters: "advfirewall firewall delete rule name=""GPT Remote PC Files 58768"""; Flags: runhidden waituntilterminated
Filename: "netsh"; Parameters: "advfirewall firewall add rule name=""GPT Remote PC TCP 58765"" dir=in action=allow protocol=TCP localport=58765 profile=any"; Flags: runhidden waituntilterminated
Filename: "netsh"; Parameters: "advfirewall firewall add rule name=""GPT Remote PC UDP 58766"" dir=in action=allow protocol=UDP localport=58766 profile=any"; Flags: runhidden waituntilterminated
Filename: "netsh"; Parameters: "advfirewall firewall add rule name=""GPT Remote PC Screen 58767"" dir=in action=allow protocol=TCP localport=58767 profile=any"; Flags: runhidden waituntilterminated
Filename: "netsh"; Parameters: "advfirewall firewall add rule name=""GPT Remote PC Files 58768"" dir=in action=allow protocol=TCP localport=58768 profile=any"; Flags: runhidden waituntilterminated
Filename: "{app}\GptRemotePC.exe"; Description: "Launch GPT Remote PC"; Flags: nowait postinstall skipifsilent

[UninstallRun]
Filename: "taskkill"; Parameters: "/F /T /IM GptRemotePC.exe"; Flags: runhidden waituntilterminated; RunOnceId: "KillGptRemotePC"

[Code]
function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  ResultCode: Integer;
begin
  Exec('taskkill', '/F /T /IM GptRemotePC.exe', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
  Sleep(500);
  Result := '';
end;
