# Import Capture Point into GitHub

The repository `https://github.com/Legionoff/CapturePoint-GPT` currently has an initial README commit.

## Windows PowerShell

1. Extract this archive. It contains the `CapturePoint` project folder.
2. Open PowerShell in the extracted `CapturePoint` folder.
3. Run the following commands:

```powershell
git clone https://github.com/Legionoff/CapturePoint-GPT.git ..\CapturePoint-GPT
Copy-Item -Path .\* -Destination ..\CapturePoint-GPT -Recurse -Force -Exclude .git
Set-Location ..\CapturePoint-GPT
git add .
git commit -m "Import Capture Point Fabric mod source"
git push origin main
```

If Git asks for identity, configure it once:

```powershell
git config --global user.name "Your GitHub name"
git config --global user.email "Your GitHub email"
```

After pushing, open the repository's **Actions** tab. The `Build Capture Point` workflow should start automatically. Open the newest run to inspect the build result. If it succeeds, download the `capturepoint-build-*` artifact from the run's **Artifacts** section; the mod JAR will be inside.

## Important

The source has not yet been verified by a successful Minecraft 26.3 compile. The workflow is intended to reveal actual dependency/API/compiler errors. A successful JAR build is not the same as a full in-game multiplayer test.
