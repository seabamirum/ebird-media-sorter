package fun.seabird;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.ImageWriteException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.common.ImageMetadata.ImageMetadataItem;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.supercsv.io.CsvListReader;
import org.supercsv.prefs.CsvPreference;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;
import com.google.common.io.Files;

import fun.seabird.MediaSortCmd.FolderGroup;
import javafx.concurrent.Task;

public class MediaSortTask extends Task<Path>
{	
	private static final Logger logger = LoggerFactory.getLogger(MediaSortTask.class);
	
	private static final DateTimeFormatter csvDtf = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a");
	private static final DateTimeFormatter imageDtf = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");
	private static final DateTimeFormatter folderDtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	
	public static final Set<String> audioExtensions = ImmutableSet.of("wav","mp3","m4a");
	public static final Set<String> videoExtensions = ImmutableSet.of("mov","m4v","mp4");
	public static final Set<String> imageExtensions = ImmutableSet.of("jpg","jpeg","png","crx","crw","cr2","cr3","crm","arw","nef","orf","raf");	
	
	static final String OUTPUT_FOLDER_NAME = "ebird";
	
	static final long MAX_ML_UPLOAD_SIZE_VIDEO = 250l;
	static final String TRANSCODED_VIDEO_SUFFIX = "_s";
	
	final String[] invalidChars = new String[] {" ",":",",",".","/","\\",">","<"};
	final String[] validChars = new String[] {"-","--","-","-","-","-","-","-"};
	
	private final List<CreationDateProvider> cdpList = ImmutableList.of
			(new ExifCreationDateProvider(),
			new FileNameCreationDateProvider(),
			new FileModifiedCreationDateProvider());
	private final RangeMap<LocalDateTime, String> rangeMap = TreeRangeMap.create();
	private final Map<String,SubStats> checklistStatsMap = new TreeMap<>();	
	private final MediaSortCmd msc;
	
	private transient Process process;
	
	public MediaSortTask(MediaSortCmd msc) {
		this.msc = msc;
	}

	private void parseCsv(File csvFile) throws FileNotFoundException, IOException
	{
		//Initialize RangeMap for quickly finding subId for a date
		try(FileInputStream myDataStream = new FileInputStream(csvFile);
			CsvListReader csvListReader = new CsvListReader(new InputStreamReader(myDataStream,"UTF-8"),CsvPreference.STANDARD_PREFERENCE))
		{			
			csvListReader.getHeader(true);
			List<String> values;
			
			logger.info("Parsing " + csvFile.getPath() + "...");
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
				String subnational1Code = values.get(5);
				String county = values.get(6);
				String locName = values.get(8);
				
				LocalDateTime subBeginTime = LocalDateTime.parse(obsDtStr + " " + obsTimeStr,csvDtf);				
				LocalDateTime subEndTime = subBeginTime.plusMinutes(duration);
				
				rangeMap.put(Range.closed(subBeginTime,subEndTime),subId);
				
				if (!checklistStatsMap.containsKey(subId))				
					checklistStatsMap.put(subId,new SubStats(subBeginTime,subnational1Code,county,locName));				
				
				int numUploaded = 0;
				if (values.size() > 22)
				{
					String assets = values.get(22);
					if (assets != null)
						numUploaded = 1+StringUtils.countMatches(assets,' ');
					
					checklistStatsMap.get(subId).incNumAssetsUploaded(numUploaded);
				}
			}
		}	
		logger.info("Done!");
	}
	
	/**
	 * @param jpegImageFile
	 * @return NULL if not an image or image is from an Apple or Google device--the metadata otherwise
	 */
	public static JpegImageMetadata shouldAdjustExif(File jpegImageFile)
    {
    	 ImageMetadata metadata;
			try {
				metadata = Imaging.getMetadata(jpegImageFile);
			} catch (ImageReadException | IOException e) 
			{
				e.printStackTrace();
				return null;
			}
			
         if (!(metadata instanceof JpegImageMetadata))
         	return null;
         
         JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;   
         
         //Don't adjust EXIF offset for mobile phones
         List<ImageMetadataItem> items = jpegMetadata.getItems();
         for (ImageMetadataItem item:items)
         {
         	String itemStr = item.toString();
         	if (StringUtils.contains(itemStr,"Make") && StringUtils.containsAny(itemStr,"Apple","Google"))
         		return null;
         }
         
         return jpegMetadata;
    }
	
    /**
     * @param jpegImageFile
     * @param dst
     * @param newDateTime
     * @return true if EXIF date successfully changed
     * @throws FileNotFoundException
     * @throws IOException
     */
    private static boolean changeDateTimeOrig(File jpegImageFile,File dst, String newDateTime) throws FileNotFoundException, IOException
    {
    	JpegImageMetadata jpegMetadata = shouldAdjustExif(jpegImageFile);
    	if (jpegMetadata == null)
    		return false;
    	
        try (FileOutputStream fos = new FileOutputStream(dst);
                OutputStream os = new BufferedOutputStream(fos)) 
        {          
            TiffImageMetadata exif = jpegMetadata.getExif();

            TiffOutputSet outputSet = null;
            if (null != exif) {               
                outputSet = exif.getOutputSet();
            }            
          
            if (null == outputSet) {
                outputSet = new TiffOutputSet();
            }

            TiffOutputDirectory exifDirectory = outputSet.getOrCreateExifDirectory();                
            exifDirectory.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL);
            exifDirectory.add(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL,newDateTime);
            new ExifRewriter().updateExifMetadataLossless(jpegImageFile, os,outputSet);            
        } 
        catch (ImageWriteException | ImageReadException e) 
        {
			e.printStackTrace();
			return false;
		}
        
        return true;
    }
    
    private static boolean isEligibleMediaFile(File f)
    {
    	if (f.isDirectory() || FileUtils.isSymlink(f))
			return false;
		
		String fileName = f.getName();
		String fileExt = Files.getFileExtension(fileName).toLowerCase();
		
		boolean isImage = imageExtensions.contains(fileExt);
		boolean isAudio= audioExtensions.contains(fileExt);
		boolean isVideo = videoExtensions.contains(fileExt);
				
		if (!isImage && !isAudio && !isVideo)
			return false;
		
		return true;
    }
    
    private void moveFile(File from,File to) throws IOException
    {
    	if (msc.isUseSymbolicLinks())
			java.nio.file.Files.createSymbolicLink(Paths.get(to.getPath()),Paths.get(from.getPath()));
		else
		{
			if (to.exists())
			{
				logger.error(to.getPath() + " already exists!! Source file left in original location.");
				return;
			}
			Files.move(from, to);
		}
    }
	
	private void checkMetadataAndMove(File f,Path outputPath,Long hrsOffset,Set<String> subIds,boolean sepYearDir,FolderGroup folderGroup) throws IOException
	{
		LocalDateTime mediaTime = null;
		for (CreationDateProvider cdp:cdpList)
		{
			mediaTime = cdp.findCreationDate(f,hrsOffset);
			if (mediaTime != null)
				break;
		}

		String parentFolderPath = outputPath.toString();
		if (sepYearDir)
		{
			parentFolderPath = parentFolderPath + File.separator + mediaTime.getYear();
			File yearDir = new File(parentFolderPath);
			if (!yearDir.exists())
				yearDir.mkdir();
		}		
		
		String subId = null;
		if (mediaTime != null)					
			subId =	rangeMap.get(mediaTime);	
		String moveToFolder;
		
		String mediaDateStr = mediaTime.format(folderDtf);
		
		if (subId != null)
		{
			SubStats ss = checklistStatsMap.get(subId);
			
			String locNameAbbrev = StringUtils.abbreviate(ss.getLocName(),"",40);
			locNameAbbrev = StringUtils.replaceEach(locNameAbbrev,invalidChars,validChars);
			
			if (FolderGroup.location == folderGroup)
			{
				File sn1 = new File(parentFolderPath + File.separator + ss.getSubnational1Code());
				if (!sn1.exists())
					sn1.mkdir();
				
				parentFolderPath = sn1.getPath();				
				if (ss.getCounty() != null)
				{
					File sn2 = new File(sn1.getPath() + File.separator + ss.getCounty());
					if (!sn2.exists())
						sn2.mkdir();
					
					parentFolderPath = sn2.getPath();
				}
				
				parentFolderPath = parentFolderPath + File.separator + locNameAbbrev;
				File locDir = new File(parentFolderPath);
				if (!locDir.exists())
					locDir.mkdir();				
			}
			else if (FolderGroup.date == folderGroup)
			{
				parentFolderPath = parentFolderPath + File.separator + mediaDateStr;
				File parentFolder = new File(parentFolderPath);
				if (!parentFolder.exists())
					parentFolder.mkdir();	
			}			
			
			String folderLocInfo = "";
			if (FolderGroup.date == folderGroup)
				folderLocInfo = ss.getSubnational1Code() + "_" + ss.getCounty() + "_" + locNameAbbrev;
			else if (FolderGroup.location == folderGroup)
				folderLocInfo = mediaDateStr;
			
			String folderName = folderLocInfo + "_" + subId;			
			String folderPath = parentFolderPath + File.separator + folderName;
			File subIdDir = new File(folderPath);
			if (!subIdDir.exists())
				subIdDir.mkdir();
			
			moveToFolder = folderPath;
			
			subIds.add(subId);
			ss.incNumAssetsLocal();
		}
		else
		{
			parentFolderPath = parentFolderPath + File.separator + mediaDateStr;
			File dateDir = new File(parentFolderPath);
			if (!dateDir.exists())
				dateDir.mkdir();
			
			moveToFolder = parentFolderPath;
		}
		
		String fileName = f.getName();
		String fileExt = Files.getFileExtension(fileName).toLowerCase();
		boolean isImage = imageExtensions.contains(fileExt);
		logger.debug("Processing " + f.getPath());
		if (hrsOffset != 0l && isImage)
		{
			String newDateTime = mediaTime.format(imageDtf);			
			if (changeDateTimeOrig(f,new File(moveToFolder + File.separator + fileName),newDateTime))
			{
				logger.debug("Adjusted EXIF date of " + f.getPath() + " to " + newDateTime);
				f.delete();
				return;
			}
		}
		
		if (msc.isTranscodeVideos() && fileExt.equals("mp4") && !fileName.contains(TRANSCODED_VIDEO_SUFFIX))
		{
			 long fileSizeInBytes = f.length();
		     long fileSizeInMB = fileSizeInBytes / (1024 * 1024);
		     if (fileSizeInMB > MAX_ML_UPLOAD_SIZE_VIDEO) 
		     {
		    	 String outputFileName = fileName.replaceFirst("[.][^.]+$", "") + TRANSCODED_VIDEO_SUFFIX + ".mp4";		    	 
		    	 File outputFile = new File(f.getParentFile() + File.separator + outputFileName);
		    	 if (!outputFile.exists())
		    	 {
			    	 logger.info(fileName + " too large for ML upload, transcoding with ffmpeg...");
			    	 String convVideoPath = moveToFolder + File.separator + outputFileName;
			    	 String[] command = {"ffmpeg", "-i", f.getAbsolutePath(), "-map_metadata", "0:s:0", "-c:v", "libx264", "-crf", "22", "-preset", "medium", "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", "-max_muxing_queue_size", "1024", convVideoPath};
			        
			         ProcessBuilder pb = new ProcessBuilder(command);
			         pb.redirectError(ProcessBuilder.Redirect.DISCARD);
			         pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
			         
			         try
			         {
	        	         process = pb.start();
	        	         int res = process.waitFor();
	        	         if (res == 0)
	        	            logger.info("Saved converted video to: " + convVideoPath);	
	        	         process.destroy();
			         }
		    	 	catch (InterruptedException | IOException e) {
	        	        logger.error("Cannot transcode video to smaller size", e);
	        	    }
		     	}
		     }
		}
			
		moveFile(f,new File(moveToFolder + File.separator + fileName));
	}	
	
	@Override
	protected Path call() throws Exception 
	{
		if (msc.getCsvFile() != null)
			parseCsv(msc.getCsvFile());		
		
		String mediaPath = msc.getMediaPath();	
		
		//make output directory inside the provided media folder
		String outputFolderName = OUTPUT_FOLDER_NAME + "_" + new Date().getTime();
		Path outputPath = Paths.get(mediaPath,outputFolderName);
		File outputDir = new File(outputPath.toUri());
		if (!outputDir.exists())
			outputDir.mkdir();			
		
		Set<String> subIds = new TreeSet<>();
		Long hrsOffset= msc.getHrsOffset();
		Iterable<File> traverser = Files.fileTraverser().depthFirstPreOrder(new File(mediaPath));
		
		List<File> eligibleFiles = new ArrayList<>();
		logger.info("Analyzing files...");
		int i=0;
		for (File f:traverser)
		{
			if (isEligibleMediaFile(f))
			{
				eligibleFiles.add(f);
				i++;
				
				if (i%100==0)
					logger.info("Added 100 files to processing queue (" + i + " total)...");
			}
		}	
		
		if (eligibleFiles.isEmpty())
		{
			logger.info("No eligible media files found.");
			return null;
		}
		
		boolean sepYearDir = msc.isSepYear();
		int numFiles = eligibleFiles.size();
		i=1;
		logger.info("Processing " + numFiles + " files in " + mediaPath + " and subdirectories...");
		for (File f:eligibleFiles)
		{
			checkMetadataAndMove(f,outputPath,hrsOffset,subIds,sepYearDir,msc.getFolderGroup());
			final double progPer = i++/((double)numFiles);
			updateProgress(progPer,1.0);
		}
		
		//Move all files out of temp parent directory we created
		if (!msc.isCreateParentDir())
		{
			logger.info("Cleaning up...");
			Iterable<File> traverser2 = Files.fileTraverser().depthFirstPreOrder(outputDir);
			for (File f:traverser2)	
			{
				if (!f.exists()) //if we move the directories, the files follow
					continue;
				
				String newPath = StringUtils.remove(f.getAbsolutePath(),outputFolderName + File.separator);
				if (f.getAbsolutePath().equals(newPath)) //could be the output dir itself
					continue;
				
				File newDir = new File(newPath);
				if (!newDir.exists())				
					Files.move(f,newDir);
				else
					logger.error("Directory " + newPath + " already exists! Check " + outputDir + " for results.");
			}
			outputDir.delete();
		}
		else
		{
			Path finalOutputPath = Paths.get(mediaPath,OUTPUT_FOLDER_NAME);
			File finalOutputDir = new File (finalOutputPath.toUri());
			if (finalOutputDir.exists())
				logger.error("Directory " + finalOutputPath + " already exists! Check " + outputDir + " for results.");
			else			
				outputDir.renameTo(finalOutputDir);
		}
		
		Path indexPath = null;
		if (!subIds.isEmpty())
		{
			indexPath = Paths.get(mediaPath,"checklistIndex_" + new Date().getTime() + ".csv");
			FileWriter fw = new FileWriter(indexPath.toString());		
			fw.write("Checklist Link,Date,State,County,Num Uploaded Assets,Num Local Assets\n");
			for (String subId:subIds)
			{
				SubStats ss = checklistStatsMap.get(subId);
				fw.write("https://ebird.org/checklist/" + subId + "/media,");
				fw.write(ss.getDate() + ",");
				fw.write(ss.getSubnational1Code() + ",");
				fw.write(ss.getCounty() + ",");
				fw.write(ss.getNumAssetsUploaded() + ",");
				fw.write(ss.getNumAssetsLocal() + "\n");
			}
			fw.close();
		}	
		
		updateProgress(1.0,1.0);
		
		logger.info("ALL DONE! :-)");
		return indexPath;
		
	}

	public Process getProcess() {
		return process;
	}
	
}