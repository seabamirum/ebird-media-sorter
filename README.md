# eBird Media Sorter

A desktop application for birders who want to efficiently organize and upload their photos, audio, and video to eBird.

## What It Does

**Automatically organize your media by date, location, and eBird checklistID:**
- Matches your photos, audio, and video files to your eBird checklists using timestamps and locations
- Creates organized folders by date (e.g., `2025-10-10/`)
- Groups media into subfolders for each checklist (e.g., `2025-10-10/US-TN_Hamilton_Standifer-Gap-Marsh_S278333280`)
- Generates a CSV index showing which checklists have matching media, making it easy to prioritize uploads

**Fix common problems:**
- Bulk adjust photo timestamps (perfect when you forget to change camera timezone while traveling)
- Reduce video file sizes below the 1GB limit for easier uploading (requires ffmpeg)
- Extract audio tracks from video files (requires ffmpeg)

## How It Works

1. Download your eBird data from eBird.org
2. Point the program to your media files and eBird CSV
3. The program matches files to checklists based on timestamps
4. Files are organized into dated folders with checklist subfolders
5. Review the generated index to see which checklists have media ready to upload

# Installation and Usage Guide

## Step 1: Download Your eBird Data

1. Go to https://ebird.org/downloadMyData
2. Download your data file after receiving the email from eBird
3. Extract the CSV file from the downloaded ZIP archive to a location you'll remember (like your Downloads folder)

## Step 2: Install Java

**Note:** This application uses JavaFX for its user interface. I recommend installing **Liberica JRE Full** which includes JavaFX bundled in - it's simpler than the standard Oracle Java.

### Windows

1. **Download and install Liberica JRE Full:**
   - Go to https://download.bell-sw.com/java/25.0.2+12/bellsoft-jre25.0.2+12-windows-amd64-full.msi
   - Run the downloaded installer and follow the prompts
   - Leave all default settings and click through to install

### macOS

1. **Download and install Liberica JRE Full:**
   - Go to https://download.bell-sw.com/java/25.0.2+12/bellsoft-jre25.0.2+12-macos-aarch64-full.dmg
   - Open the downloaded DMG file and drag the application to your Applications folder
   - Follow any additional prompts to complete installation
   - Open Terminal and verify with `java -version`

### Linux (Ubuntu/Debian)

1. Separate JRE installation is not required as it's included in the /bin directory of the tar.gz executable under [Releases page](../../releases)

## Step 3: Download the eBird Media Sorter

1. Go to the [Releases page](../../releases) in this repository
2. Download the latest `ebird-media-sorter-[version].jar` file
3. Save it to an easy-to-find location like your Downloads folder

## Step 4: Run the Program

### Windows

1. Press `Windows Key + R` to open the Run dialog
2. Type `cmd` and press Enter to open Command Prompt
3. Navigate to your Downloads folder by typing: `cd %USERPROFILE%\Downloads` and pressing Enter
4. Run the program by typing: `java -jar ebird-media-sorter-[version].jar` (replace `[version]` with the actual version number)
5. Press Enter

### macOS

1. Open Terminal (press `Command + Space`, type "Terminal", press Enter)
2. Navigate to your Downloads folder by typing: `cd ~/Downloads` and pressing Enter
3. Run the program by typing: `java -jar ebird-media-sorter-[version].jar` (replace `[version]` with the actual version number)
4. Press Enter

### Linux

1. Extract the downloaded tar.gz file and run the eBird Media Sorter executable in the bin directory

---

## Troubleshooting

**"java is not recognized as an internal or external command"** (Windows)
- Java is not installed or not in your PATH. Review Step 2 above.

**"command not found: java"** (macOS/Linux)
- Java is not installed. Review Step 2 above.

**"Unable to access jarfile"**
- Make sure you're in the correct directory where you downloaded the .jar file
- Check that the filename matches exactly (including the version number)

**Need more help?**
- Open an issue in this repository with details about your operating system and the error message you're seeing



# Screenshots

Application (Linux/Ubuntu):

![Screenshot_20230207_100507](https://user-images.githubusercontent.com/3449269/217298547-d48ce8db-74fd-49e4-927f-8d10023a45bb.png)

Generated CSV file:

![Screenshot_20230207_101153](https://user-images.githubusercontent.com/3449269/217300534-6c5f9986-7a82-46e9-ba7f-2789e6068ec6.png)
