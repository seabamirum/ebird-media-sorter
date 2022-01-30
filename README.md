# ebird-media-sorter
Program to sort photos, audio, and video on the file system by date and eBird checklistId. All media determined to be created on a given date will be put underneath a folder named with that date. If a checklistId match is found, sub-folders for each checklistId will be created. Generates an index file for all checklistId matches, so you can easily find and prioritize which lists to upload media for, and mark them done when completed.

(1) Download the most recent Java JRE (Version 17) if it's not already installed https://java.com/en/download/
(2) Download the <a href="https://github.com/seabamirum/ebird-media-sorter/releases/download"> ebird-media-sorter</a> jar file (under RELEASES)
(3) Open a command prompt and switch to the directory that has your downloaded JAR file
(4) Run the program using by typing in: java -jar ebird-media-sorter-[version].jar arg1 arg2 (arg3)
  Argument 1 to the program is the directory of your "MyEBirdData.csv" file from https://ebird.org/downloadMyData
  Argument 2 is the location of the directory where you would like to reorganize your media, e.g. /home/Pictures
  Argument 3 (optional) is an offset in hours, if your device time is known to be off a number of hours from local time for a folder. The default is 0.

WARNING: This program moves any photo, video, or, audio file to an 'ebird' subdirectory whether it finds a checklistId match or not. In most cases, it is convenient to have images, etc. organized by creation date in this way. But if you have already grouped photos by region, trip, etc. you may not want to run this on your root Pictures directory. I recommend starting with a smaller directory first. 

NOTE: By default, any non-media file, or any media file under a directory called 'ebird' will be ignored. To re-run the process on the same photos (for example, if you find afterwards that your EXIF data is off by an hour or two), simply ensure that 'ebird' no longer appears in the directory hierarchy, either by moving the folder out, or by renaming the parent directory.
