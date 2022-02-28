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

import javax.swing.JProgressBar;

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
import org.supercsv.io.CsvListReader;
import org.supercsv.prefs.CsvPreference;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;
import com.google.common.io.Files;

public class MediaSorterRunner 
{	
	private static final DateTimeFormatter csvDtf = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a");
	private static final DateTimeFormatter imageDtf = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");
	private static final DateTimeFormatter folderDtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	
	public static final Set<String> audioExtensions = ImmutableSet.of("wav","mp3","m4a");
	public static final Set<String> videoExtensions = ImmutableSet.of("mov","m4v","mp4");
	public static final Set<String> imageExtensions = ImmutableSet.of("jpg","jpeg","png","crx","crw","cr2","cr3","crm","arw","nef","orf","raf");	
	
	static final String OUTPUT_FOLDER_NAME = "ebird";
	
	private final List<CreationDateProvider> cdpList = ImmutableList.of
			(new ExifCreationDateProvider(),
			new FileNameCreationDateProvider(),
			new FileModifiedCreationDateProvider());
	private final RangeMap<LocalDateTime, String> rangeMap = TreeRangeMap.create();
	private final Map<String,SubStats> checklistStatsMap = new TreeMap<>();	
	private final MediaSortCmd msc;
	
	public MediaSorterRunner(MediaSortCmd msc) {
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
			
			System.out.print("Parsing " + csvFile.getPath() + "...");
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
				
				LocalDateTime subBeginTime = LocalDateTime.parse(obsDtStr + " " + obsTimeStr,csvDtf);				
				LocalDateTime subEndTime = subBeginTime.plusMinutes(duration);
				
				rangeMap.put(Range.closed(subBeginTime,subEndTime),subId);
				
				if (!checklistStatsMap.containsKey(subId))				
					checklistStatsMap.put(subId,new SubStats(subBeginTime,subnational1Code,county));				
				
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
		System.out.println("Done!");
	}	
	
    /**
     * @param jpegImageFile
     * @param dst
     * @param newDateTime
     * @return true if EXIF date successfully changed
     * @throws FileNotFoundException
     * @throws IOException
     */
    private static boolean changeDateTimeOrig(File jpegImageFile, File dst, String newDateTime) throws FileNotFoundException, IOException
    {
        try (FileOutputStream fos = new FileOutputStream(dst);
                OutputStream os = new BufferedOutputStream(fos)) 
        {
            ImageMetadata metadata;
			try {
				metadata = Imaging.getMetadata(jpegImageFile);
			} catch (ImageReadException | IOException e) 
			{
				e.printStackTrace();
				return false;
			}
			
            if (!(metadata instanceof JpegImageMetadata))
            	return false;
            
            JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;   
            
            //Don't adjust EXIF offset for mobile phones
            List<ImageMetadataItem> items = jpegMetadata.getItems();
            for (ImageMetadataItem item:items)
            {
            	String itemStr = item.toString();
            	if (StringUtils.contains(itemStr,"Make") && StringUtils.containsAny(itemStr,"Apple","Google"))
            		return false;
            }
                        
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
    		Files.move(from,to);
    }
	
	private void checkMetadataAndMove(File f,Path outputPath,Long hrsOffset,Set<String> subIds,boolean sepYearDir) throws IOException
	{
		LocalDateTime mediaTime = null;
		for (CreationDateProvider cdp:cdpList)
		{
			mediaTime = cdp.findCreationDate(f,hrsOffset);
			if (mediaTime != null)
				break;
		}

		String dateDirPath;
		if (sepYearDir)
		{
			String yearDirPath = outputPath + File.separator + mediaTime.getYear();
			File yearDir = new File(yearDirPath);
			if (!yearDir.exists())
				yearDir.mkdir();
			
			dateDirPath = yearDirPath + File.separator + mediaTime.format(folderDtf);
		}	
		else
			dateDirPath = outputPath + File.separator + mediaTime.format(folderDtf);
		
		File dateDir = new File(dateDirPath);
		if (!dateDir.exists())
			dateDir.mkdir();
		
		String subId = null;
		if (mediaTime != null)					
			subId =	rangeMap.get(mediaTime);	
		File movedFile;
		if (subId != null)
		{
			String subIdPath = dateDirPath + File.separator + subId;
			File subIdDir = new File(subIdPath);
			if (!subIdDir.exists())
				subIdDir.mkdir();
			
			movedFile = new File(subIdPath + File.separator + f.getName());
			
			subIds.add(subId);
			checklistStatsMap.get(subId).incNumAssetsLocal();
		}
		else
			movedFile = new File(dateDirPath + File.separator + f.getName());
		
		boolean isImage = imageExtensions.contains(Files.getFileExtension(f.getName()).toLowerCase());
		System.out.println("Processing " + f.getPath());
		if (hrsOffset != 0l && isImage)
		{
			String newDateTime = mediaTime.format(imageDtf);			
			if (changeDateTimeOrig(f,movedFile,newDateTime))
			{
				System.out.println("Adjusted EXIF date of " + f.getPath() + " to " + newDateTime);
				f.delete();
			}
			else
				moveFile(f,movedFile);
		}
		else		
			moveFile(f,movedFile);
	}	
	
	public Path run() throws FileNotFoundException, IOException
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
		for (File f:traverser)
		{
			if (isEligibleMediaFile(f))
				eligibleFiles.add(f);
		}	
		
		if (eligibleFiles.isEmpty())
		{
			System.out.println("No eligible media files found.");
			return null;
		}
		
		boolean sepYearDir = msc.isSepYear();
		int numFiles = eligibleFiles.size();
		int i=1;
		JProgressBar pb = msc.getPb();
		System.out.println("Processing " + numFiles + " files in " + mediaPath + " and subdirectories...");
		for (File f:eligibleFiles)
		{			
			checkMetadataAndMove(f,outputPath,hrsOffset,subIds,sepYearDir);
			if (pb != null)
				pb.setValue((int) (((i++)/((double)numFiles))*100));
		}
		
		//Move all files out of temp parent directory we created
		if (!msc.isCreateParentDir())
		{
			System.out.println("Cleaning up...");
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
					System.err.println("Directory " + newPath + " already exists! Check " + outputDir + " for results.");
			}
			outputDir.delete();
		}
		else
		{
			Path finalOutputPath = Paths.get(mediaPath,OUTPUT_FOLDER_NAME);
			File finalOutputDir = new File (finalOutputPath.toUri());
			if (finalOutputDir.exists())
				System.err.println("Directory " + finalOutputPath + " already exists! Check " + outputDir + " for results.");
			else			
				outputDir.renameTo(finalOutputDir);
		}
		
		Path indexPath = null;
		if (!subIds.isEmpty())
		{
			indexPath = Paths.get(mediaPath,"checklistIndex_" + new Date().getTime() + ".csv");
			FileWriter fw = new FileWriter(indexPath.toString());		
			fw.write("Checklist Link,Date,Subnat1,County,Num Uploaded,Num Local\n");
			for (String subId:subIds)
			{
				SubStats ss = checklistStatsMap.get(subId);
				fw.write("https://ebird.org/checklist/" + subId + ",");
				fw.write(ss.getDate() + ",");
				fw.write(ss.getSubnational1Code() + ",");
				fw.write(ss.getCounty() + ",");
				fw.write(ss.getNumAssetsUploaded() + ",");
				fw.write(ss.getNumAssetsLocal() + "\n");
			}
			fw.close();
		}	
		
		System.out.println("ALL DONE! :-)");
		return indexPath;
		
	} //END main
}