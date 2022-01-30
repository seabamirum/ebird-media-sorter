# ebird-media-sorter
Program to sort photos, audio, and video on the file system by date and eBird checklistId. All media determined to be created on a given date will be put underneath a folder named with that date. If a checklistId match is found, sub-folders for each checklistId will be created. Generates an index file for all checklistId matches, so you can easily find and prioritize which lists to upload media for, and mark them done when completed.

(1) Download the most recent Java JRE (Version 17) if it's not already installed https://java.com/en/download/<br/>
(2) Download the <a href="https://github.com/seabamirum/ebird-media-sorter/releases"> ebird-media-sorter-[version]</a> jar file (under RELEASES)<br/>
(3) Open a command prompt and switch to the directory that has your downloaded JAR file<br/>
(4) Run the program by typing in: java -jar ebird-media-sorter-[version].jar arg1 arg2 (arg3)<br/>
  Argument 1 to the program is the directory of your "MyEBirdData.csv" file from https://ebird.org/downloadMyData<br/>
  Argument 2 is the location of the directory where you would like to reorganize your media, e.g. /home/Pictures<br/>
  Argument 3 (optional) is an offset in hours, if your device time is known to be off a number of hours from local time for a folder. The default is 0.

WARNING: This program moves any photo (jpg,jpeg), video (mov,m4f,mp4), or audio (wav,mp3,m4a) file to an 'ebird/YYYY-MM-DD' subdirectory  whether it finds a checklistId match or not. If a creation date cannot be read from EXIF, file last modified date is used. In most cases, it is convenient to have files organized by date in this way. But if you have already grouped photos by region, trip, etc. you may not want to run this on your root media directory. I recommend starting with a smaller directory first to understand the process first. 

NOTE: By default, any non-media file, or any media file underneath a directory called 'ebird' will be ignored. To re-run the process on the same photos (for example, if you find afterwards that your EXIF data is off by an hour or two and you would like more checklistId matches), simply ensure that 'ebird' no longer appears in the directory hierarchy, either by moving the folder out, or by renaming 'ebird' to something else.
