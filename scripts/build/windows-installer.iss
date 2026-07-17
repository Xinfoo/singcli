; Build the installer:
;   ISCC.exe scripts\build\windows-installer.iss
;
; If sing-box\sing-box.exe and sing-box\LICENSE both exist under the project
; root, the installer includes them and the DLL files from the same directory.
; Otherwise it builds singcli only.
; Override the package version with /DMyAppVersion=1.2.2.

#define MyAppName "singcli"
#define MyAppPublisher "Xinfoo"
#define MyAppUrl "https://github.com/Xinfoo/singcli"

#ifndef MyAppVersion
  #define MyAppVersion "1.2.2"
#endif

#define ProjectRoot AddBackslash(SourcePath) + "..\.."
#define SingBoxDir ProjectRoot + "\sing-box"
#define SingBoxExe SingBoxDir + "\sing-box.exe"
#define SingBoxLicense SingBoxDir + "\LICENSE"
#define IncludeSingBox FileExists(SingBoxExe) && FileExists(SingBoxLicense)

[Setup]
AppId=singcli
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppUrl}
AppSupportURL={#MyAppUrl}/issues
AppUpdatesURL={#MyAppUrl}/releases
DefaultDirName={autopf}\singcli
DefaultGroupName=singcli
DisableProgramGroupPage=yes
LicenseFile={#ProjectRoot}\LICENSE
OutputDir={#ProjectRoot}\dist\windows
OutputBaseFilename=singcli-{#MyAppVersion}-windows-setup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
PrivilegesRequiredOverridesAllowed=dialog commandline
ChangesEnvironment=yes
UninstallDisplayName=singcli
UsePreviousComponents=no
UsePreviousSetupType=no

[Types]
#if IncludeSingBox
Name: "full"; Description: "Full installation"
#else
Name: "compact"; Description: "singcli"
#endif
Name: "custom"; Description: "Custom installation"; Flags: iscustom

[Components]
#if IncludeSingBox
Name: "core"; Description: "singcli command-line application"; Types: full custom; Flags: fixed
Name: "singbox"; Description: "Bundled sing-box executable"; Types: full
#else
Name: "core"; Description: "singcli command-line application"; Types: compact custom; Flags: fixed
#endif

[Tasks]
Name: "addtopath"; Description: "Add the installation directory to PATH"; Flags: checkedonce

[Files]
Source: "{#ProjectRoot}\dist\singcli.jar"; DestDir: "{app}"; Components: core; Flags: ignoreversion
Source: "{#ProjectRoot}\scripts\windows\singcli.cmd"; DestDir: "{app}"; Components: core; Flags: ignoreversion
Source: "{#ProjectRoot}\LICENSE"; DestDir: "{app}"; DestName: "LICENSE-singcli.txt"; Components: core; Flags: ignoreversion
#if IncludeSingBox
Source: "{#SingBoxExe}"; DestDir: "{app}"; Components: singbox; Flags: ignoreversion
Source: "{#SingBoxDir}\*.dll"; DestDir: "{app}"; Components: singbox; Flags: ignoreversion skipifsourcedoesntexist
Source: "{#SingBoxLicense}"; DestDir: "{app}"; DestName: "LICENSE-sing-box.txt"; Components: singbox; Flags: ignoreversion
#endif

[Code]
const
  MachineEnvironmentKey = 'SYSTEM\CurrentControlSet\Control\Session Manager\Environment';
  UserEnvironmentKey = 'Environment';

function EnvironmentRoot: Integer;
begin
  if IsAdminInstallMode then
    Result := HKLM
  else
    Result := HKCU;
end;

function EnvironmentKey: String;
begin
  if IsAdminInstallMode then
    Result := MachineEnvironmentKey
  else
    Result := UserEnvironmentKey;
end;

function TrimTrailingBackslash(Value: String): String;
begin
  Result := Value;
  while (Length(Result) > 3) and (Result[Length(Result)] = '\') do
    Delete(Result, Length(Result), 1);
end;

function PathContains(const PathValue, Directory: String): Boolean;
var
  Remaining: String;
  Entry: String;
  Separator: Integer;
  Expected: String;
begin
  Result := False;
  Remaining := PathValue;
  Expected := TrimTrailingBackslash(Directory);

  while Remaining <> '' do
  begin
    Separator := Pos(';', Remaining);
    if Separator = 0 then
    begin
      Entry := Remaining;
      Remaining := '';
    end
    else
    begin
      Entry := Copy(Remaining, 1, Separator - 1);
      Delete(Remaining, 1, Separator);
    end;

    Entry := TrimTrailingBackslash(Trim(Entry));
    if CompareText(Entry, Expected) = 0 then
    begin
      Result := True;
      Exit;
    end;
  end;
end;

procedure AddToPath;
var
  CurrentPath: String;
  UpdatedPath: String;
begin
  if not RegQueryStringValue(EnvironmentRoot, EnvironmentKey, 'Path', CurrentPath) then
    CurrentPath := '';

  if PathContains(CurrentPath, ExpandConstant('{app}')) then
    Exit;

  if CurrentPath = '' then
    UpdatedPath := ExpandConstant('{app}')
  else
    UpdatedPath := ExpandConstant('{app}') + ';' + CurrentPath;

  if not RegWriteExpandStringValue(EnvironmentRoot, EnvironmentKey, 'Path', UpdatedPath) then
    RaiseException('Could not add the installation directory to PATH.');
end;

procedure RemoveFromPath;
var
  CurrentPath: String;
  Remaining: String;
  Entry: String;
  UpdatedPath: String;
  Separator: Integer;
  InstallDirectory: String;
begin
  if not RegQueryStringValue(EnvironmentRoot, EnvironmentKey, 'Path', CurrentPath) then
    Exit;

  Remaining := CurrentPath;
  UpdatedPath := '';
  InstallDirectory := TrimTrailingBackslash(ExpandConstant('{app}'));

  while Remaining <> '' do
  begin
    Separator := Pos(';', Remaining);
    if Separator = 0 then
    begin
      Entry := Remaining;
      Remaining := '';
    end
    else
    begin
      Entry := Copy(Remaining, 1, Separator - 1);
      Delete(Remaining, 1, Separator);
    end;

    Entry := Trim(Entry);
    if (Entry <> '') and
       (CompareText(TrimTrailingBackslash(Entry), InstallDirectory) <> 0) then
    begin
      if UpdatedPath <> '' then
        UpdatedPath := UpdatedPath + ';';
      UpdatedPath := UpdatedPath + Entry;
    end;
  end;

  if not RegWriteExpandStringValue(EnvironmentRoot, EnvironmentKey, 'Path', UpdatedPath) then
    RaiseException('Could not remove the installation directory from PATH.');
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if (CurStep = ssPostInstall) and WizardIsTaskSelected('addtopath') then
    AddToPath;
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usUninstall then
    RemoveFromPath;
end;
