# ebird-media-sorter
Program to sort photos, audio, and video on the file system by date and eBird checklistId. EXIF creation date can also be adjusted in batch for images. All media determined to be created on a given date will be put underneath a folder named with that date, e.g. '2022-01-29/'. If a checklistId match is found, sub-folders for each checklistId will be created, e.g. '2022-01-29/S2626262/'. When the process completes, it generates an index CSV file for all checklistId matches, so that you can easily find and prioritize which lists to upload media for, and mark them done when completed.

1. Download your eBird data from https://ebird.org/downloadMyData and extract the CSV file<br/>
2. Download a (non-headless) Java JRE, minimum Version 17 if it's not already installed. To check current installation status and what version you have, open a command prompt and type `java -version`. Newer versions can be obtained using the following<br/>
    - Windows/Mac: From https://jdk.java.net/17/<br/>
    - Linux: from command line run `sudo apt install openjdk-17-jre`<br/>
3. Browse to [RELEASES](../../releases) in this repo and download the latest `ebird-media-sorter-[version].jar` file
4. Open a command prompt and switch to the directory (e.g. `cd C:/downloads`) that has your downloaded JAR file<br/>
5. Run the program by typing in the command prompt: `java -jar ebird-media-sorter-[version].jar`<br/>

WARNING: This program by default moves any eBird-supported photo, video, or audio file to date (YYYY-MM-DD) subdirectories whether it finds a checklistId match or not. If a creation date cannot be read from EXIF, it looks for a date in the beginning of the file name, and finally file last-modified date is used. If you have already grouped your media in some other fashion, you may want to use the checkbox to generate symbolic (shorcut) link files instead. I recommend starting with a small media directory first to understand the process. 

Generated CSV file:

![Screenshot_20220217_113905](https://user-images.githubusercontent.com/3449269/154528416-0e588227-f45b-4684-ae19-07b61620a745.png)
