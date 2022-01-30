package fun.seabird;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.supercsv.io.CsvListReader;
import org.supercsv.prefs.CsvPreference;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.mov.media.QuickTimeMediaDirectory;
import com.drew.metadata.mp4.Mp4Directory;
import com.drew.metadata.wav.WavDirectory;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;
import com.google.common.io.Files;

public abstract class MediaSorter 
{	
	private static final DateTimeFormatter csvDtf = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a");
	private static final DateTimeFormatter imageDtf = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");
	private static final DateTimeFormatter videoDtf = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy");
	private static final DateTimeFormatter folderDtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	
	private static final RangeMap<LocalDateTime, String> rangeMap = TreeRangeMap.create();	
	
	private static final Map<String,SubStats> checklistStatsMap = new TreeMap<>();
	
	private static final Set<String> audioExtensions = ImmutableSet.of("wav","mp3","m4a");
	private static final Set<String> videoExtensions = ImmutableSet.of("mov","m4v","mp4");
	private static final Set<String> imageExtensions = ImmutableSet.of("jpg","jpeg","cr2");	
	
	private static final String OUTPUT_FOLDER_NAME = "ebird";
	
	private static void parseCsv(String csvPath) throws FileNotFoundException, IOException
	{
		//Initialize RangeMap for quickly finding subId for a date
		try(FileInputStream myDataStream = new FileInputStream(csvPath + File.separator + "MyEBirdData.csv");
			CsvListReader csvListReader = new CsvListReader(new InputStreamReader(myDataStream,"UTF-8"),CsvPreference.STANDARD_PREFERENCE))
		{			
			csvListReader.getHeader(true);
			List<String> values;
			
			System.out.println("Parsing CSV file...");
			while ((values = csvListReader.read()) != null)
			{
				Integer duration = 0;
				String durationStr = values.get(14);
				if (durationStr != null)
					duration = Integer.valueOf(durationStr);
				
				if (duration <= 0)
					continue;
				
				String obsTimeStr = values.get(12);
				
				if (obsTimeStr == null)
					continue;
					
				String subId = values.get(0);
				String obsDtStr = values.get(11);
				
				LocalDateTime subBeginTime = LocalDateTime.parse(obsDtStr + " " + obsTimeStr,csvDtf);				
				LocalDateTime subEndTime = subBeginTime.plusMinutes(duration);
				
				rangeMap.put(Range.closed(subBeginTime,subEndTime),subId);
				
				if (!checklistStatsMap.containsKey(subId))				
					checklistStatsMap.put(subId,new SubStats(subBeginTime));				
				
				int numUploaded = 0;
				if (values.size() > 22)
				{
					String assets = values.get(22);
					if (assets != null)
						numUploaded = 1+StringUtils.countMatches(assets,' ');
					
					checklistStatsMap.get(subId).incNumAssetsUploaded(numUploaded);
				}
			}
			System.out.println("DONE parsing!\nTraversing directory...");
		}	
	}
	
	private static void checkMetadataAndMove(File f,String outputPath,Long hrsOffset,Set<String> subIds) throws IOException
	{
		if (f.isDirectory() || f.getPath().contains(OUTPUT_FOLDER_NAME))
			return;
		
		String fileName = f.getName();
		String fileExt = Files.getFileExtension(fileName).toLowerCase();
		
		boolean isImage = imageExtensions.contains(fileExt);
		boolean isAudio= audioExtensions.contains(fileExt);
		boolean isVideo = videoExtensions.contains(fileExt);
				
		if (!isImage && !isAudio && !isVideo)
			return;
		
		FileInputStream mediaStream = new FileInputStream(f);
		Metadata metadata = null; 
		try {
			metadata = ImageMetadataReader.readMetadata(mediaStream);		
		}
		catch(ImageProcessingException ipe)
		{
			System.err.println("Error reading " + fileName + ": " + ipe);
		}
		catch(StringIndexOutOfBoundsException sobe)
		{
			System.err.println("Error reading " + fileName + ": " + sobe);
		}		
		
		Directory directory = null; 
		String dateTimeOrigStr = null;
		if (metadata != null)
		{
			if (isImage)
				directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
			else if (isVideo)
			{
				if (fileExt.equals("mp4"))
					directory = metadata.getFirstDirectoryOfType(Mp4Directory.class);
				else if (fileExt.equals("mov"))
					directory = metadata.getFirstDirectoryOfType(QuickTimeMediaDirectory.class);				
			}
			else if (isAudio && fileExt.equals("wav"))
					directory = metadata.getFirstDirectoryOfType(WavDirectory.class);
			
			if (directory != null)
			{
		        if (isImage)
		        	dateTimeOrigStr = directory.getString(ExifDirectoryBase.TAG_DATETIME_ORIGINAL);
		        else if (isVideo)
		        	if (fileExt.equals("mp4"))
		        		dateTimeOrigStr = directory.getString(Mp4Directory.TAG_CREATION_TIME);
					else if (fileExt.equals("mov"))
						dateTimeOrigStr = directory.getString(QuickTimeMediaDirectory.TAG_CREATION_TIME);
		        else if (isAudio)
		        	dateTimeOrigStr = directory.getString(WavDirectory.TAG_DATE_CREATED);	
			}
		}
        
		LocalDateTime mediaTime = null;
		if (dateTimeOrigStr == null)
		{	//use file last modified date if we can't get anything from EXIF
			dateTimeOrigStr = new Date(f.lastModified()).toString();
			if (dateTimeOrigStr == null)
				return;
			mediaTime = LocalDateTime.parse(dateTimeOrigStr,videoDtf);
		}
		else
		{
			try 
			{
				if (isImage)
					mediaTime = LocalDateTime.parse(dateTimeOrigStr,imageDtf);
				else
					mediaTime = LocalDateTime.parse(dateTimeOrigStr,videoDtf);					
			} 
			catch (DateTimeParseException dtpe) 
			{
				System.err.println("Can't find creation time for " + fileName);
				return;
			}
		}
		
		if (hrsOffset != 0)
			mediaTime = mediaTime.plusHours(hrsOffset);

		String datePath = outputPath + File.separator + mediaTime.format(folderDtf);
		
		File dateDir = new File(datePath);
		if (!dateDir.exists())
			dateDir.mkdir();
		
		String subId = null;
		if (mediaTime != null)					
			subId =	rangeMap.get(mediaTime);	
		File movedFile;
		if (subId != null)
		{
			String subIdPath = datePath + File.separator + subId;
			File subIdDir = new File(subIdPath);
			if (!subIdDir.exists())
				subIdDir.mkdir();
			
			movedFile = new File(subIdPath + File.separator + f.getName());
			
			subIds.add(subId);
			checklistStatsMap.get(subId).incNumAssetsLocal();
		}
		else
			movedFile = new File(datePath + File.separator + f.getName());
		
		System.out.println("Moving " + fileName + " to " + movedFile.getPath());
		Files.move(f,movedFile);
	}
	
	public static void main(String[] args) throws IOException
	{
		if (args.length < 2)
		{
			System.out.println("USAGE: 1st argument is path to directory that contains MyEBirdData.csv file (e.g. downloads/ebird). 2nd argument is path to media directory.");
			return;
		}
		
		parseCsv(args[0]);
		
		String mediaPath = args[1];		
		
		//make output directory inside the provided media folder
		String outputPath = mediaPath + File.separator + OUTPUT_FOLDER_NAME;
		File outputDir = new File(outputPath);
		if (!outputDir.exists())
			outputDir.mkdir();			
		
		Set<String> subIds = new TreeSet<>();
		Long hrsOffset= args.length == 3 ? Long.valueOf(args[2]) : 0l;
		Iterable<File> traverser = Files.fileTraverser().depthFirstPreOrder(new File(mediaPath));
		for (File f:traverser)
			checkMetadataAndMove(f,outputPath,hrsOffset,subIds);
		
		if (!subIds.isEmpty())
		{
			FileWriter fw = new FileWriter(mediaPath + File.separator + "checklistIndex_" + subIds.size() + ".csv");		
			fw.write("Checklist Link,Date,Num Assets Uploaded,Num Assets Local\n");
			for (String subId:subIds)
			{
				SubStats ss = checklistStatsMap.get(subId);
				fw.write("https://ebird.org/checklist/" + subId + ",");
				fw.write(ss.getDate() + ",");
				fw.write(ss.getNumAssetsUploaded() + ",");
				fw.write(ss.getNumAssetsLocal() + "\n");
			}
			fw.close();
		}
		
		System.out.println("ALL DONE! Now upload your media to eBird...");
		
	} //END main
}