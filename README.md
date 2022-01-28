# ebird-media-sorter
Program to sort photos, audio, and video on the file system by date and eBird checklistId

Download the most recent Java JRE (Version 17) if it's not already installed https://java.com/en/download/
Download ebird-media-sorter.jar (under RELEASES tab)
Open a command prompt
Switch to the directory that has ebird-media-sorter.jar
Run the program using java -jar ebird-media-sorter.jar arg1 arg2 (arg3)
  Argument 1 to the program is the directory of your "MyEBirdData.csv" file from https://ebird.org/downloadMyData
  Argument 2 is the location of your media directory, e.g. /home/Pictures
  Argument 3 (optional) is an offset in hours, if your device time is known to be off a number of hours from actual time for a folder. The default is 0.

NOTE: By default, any non-media file, or any media file under a directory called 'ebird' will be ignored. To re-run the process on the same photos, simply rename the 'ebird' directory to something else and they will be moved out of there on the next run.

WARNING: This program moves any photo, video, or, audio file to an 'ebird' subdirectory whether it finds a checklistId match or not. In most cases, it is convenient to have images, etc. organized by creation date in this way. But if you have already grouped photos by region, trip, etc. you may not want to run this on your root Pictures directory. I recommend starting with a smaller directory first. 
