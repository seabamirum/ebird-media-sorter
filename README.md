# eBird Media Sorter
This program organizes photos, audio, and video files on your file system by date or location, using information from your downloaded eBird data CSV file. It also allows you to adjust the EXIF creation date of images in bulk. The files are sorted into folders named with their creation date, such as '2022-01-29/', and if a checklistId match is found, additional sub-folders are created for each checklist, such as '2022-01-29/S2626262/'. Once the sorting process is complete, the program generates a CSV index file for all checklistId matches, making it easy for you to prioritize which lists to upload media for, and to keep track of which ones you have already completed.

# Usage

1. Download your eBird data from https://ebird.org/downloadMyData and extract the CSV file<br/>
2. Download a (non-headless) Java JRE, minimum Version 21 if it's not already installed. To check current installation status and what version you have, open a command prompt and type `java -version`. Newer versions can be obtained using the following<br/>
    - Windows/Mac: From https://jdk.java.net/21/<br/>
    - Linux: from command line run `sudo apt install openjdk-21-jre`<br/>
3. Browse to [RELEASES](../../releases) in this repo and download the latest `ebird-media-sorter-[version].jar` file
4. Open a command prompt and switch to the directory (e.g. `cd C:/downloads`) that has your downloaded JAR file<br/>
5. Run the program by typing in the command prompt: `java -jar ebird-media-sorter-[version].jar`<br/>

Warning: This program by default moves any eBird-supported photo, video, or audio file to date (YYYY-MM-DD) subdirectories whether it finds a checklistId match or not. If a creation date cannot be read from EXIF, it looks for a date in the beginning of the file name, and finally file last-modified date is used. If you have already grouped your media in some other fashion, you may want to use the checkbox to generate symbolic (shorcut) link files instead. I recommend starting with a small media directory first to understand the process. 

# Screenshots

Application (Linux/Ubuntu):

![Screenshot_20230207_100507](https://user-images.githubusercontent.com/3449269/217298547-d48ce8db-74fd-49e4-927f-8d10023a45bb.png)

Generated CSV file:

![Screenshot_20230207_101153](https://user-images.githubusercontent.com/3449269/217300534-6c5f9986-7a82-46e9-ba7f-2789e6068ec6.png)
