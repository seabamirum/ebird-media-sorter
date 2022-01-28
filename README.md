# ebird-media-sorter
Program to sort photos, audio, and video on the file system by date and eBird checklistId

Download the Java JRE if it's not already installed https://java.com/en/download/
Download ebird-media-sorter.jar (under RELEASES tab)
Open a command prompt
Switch to the directory that has ebird-media-sorter.jar
Argument 1 to the program is the directory of your "MyEBirdData.csv" file from https://ebird.org/downloadMyData
Argument 2 is the location of your media directory, e.g. /home/Pictures
Argument 3 (optional) is an offset in hours, if your device time is known to be off a number of hours from actual time for a folder. The default is 0.
Run the program using java -jar ebird-media-sorter.jar arg1 arg2 [arg3]
