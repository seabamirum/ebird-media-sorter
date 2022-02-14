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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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

public class MediaSorterRunner 
{	
	private static final DateTimeFormatter csvDtf = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a");
	private static final DateTimeFormatter imageDtf = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");
	private static final DateTimeFormatter timezoneDtf = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy");
	private static final DateTimeFormatter folderDtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	
	private static final Set<String> audioExtensions = ImmutableSet.of("wav","mp3","m4a");
	private static final Set<String> videoExtensions = ImmutableSet.of("mov","m4v","mp4");
	private static final Set<String> imageExtensions = ImmutableSet.of("jpg","jpeg","png","crx","crw","cr2","cr3","crm","arw","nef","orf","raf");	
	
	static final String OUTPUT_FOLDER_NAME = "ebird";
	
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
	
    private static void changeDateTimeOrig(final File jpegImageFile, final File dst, String newDateTime)
            throws IOException, ImageReadException, ImageWriteException {

        try (FileOutputStream fos = new FileOutputStream(dst);
                OutputStream os = new BufferedOutputStream(fos)) {

            TiffOutputSet outputSet = null;

            // note that metadata might be null if no metadata is found.
            final ImageMetadata metadata = Imaging.getMetadata(jpegImageFile);
            
            if (!(metadata instanceof JpegImageMetadata))
            	throw new ImageReadException("Can only modify EXIF of jpg files");
            
            final JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;          
            final TiffImageMetadata exif = jpegMetadata.getExif();

            if (null != exif) {
                // TiffImageMetadata class is immutable (read-only).
                // TiffOutputSet class represents the Exif data to write.
                //
                // Usually, we want to update existing Exif metadata by
                // changing
                // the values of a few fields, or adding a field.
                // In these cases, it is easiest to use getOutputSet() to
                // start with a "copy" of the fields read from the image.
                outputSet = exif.getOutputSet();
            }
            
            // if file does not contain any exif metadata, we create an empty
            // set of exif metadata. Otherwise, we keep all of the other
            // existing tags.
            if (null == outputSet) {
                outputSet = new TiffOutputSet();
            }

            {
                // Example of how to add a field/tag to the output set.
                //
                // Note that you should first remove the field/tag if it already
                // exists in this directory, or you may end up with duplicate
                // tags. See above.
                //
                // Certain fields/tags are expected in certain Exif directories;
                // Others can occur in more than one directory (and often have a
                // different meaning in different directories).
                //
                // TagInfo constants often contain a description of what
                // directories are associated with a given tag.
                //
                final TiffOutputDirectory exifDirectory = outputSet.getOrCreateExifDirectory();
                // make sure to remove old value if present (this method will
                // not fail if the tag does not exist).
                exifDirectory.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL);
                exifDirectory.add(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL,newDateTime);
            }

            new ExifRewriter().updateExifMetadataLossless(jpegImageFile, os,outputSet);
        }
    }
    
    private static boolean isEligibleMediaFile(File f)
    {
    	if (f.isDirectory())
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
	
	private void checkMetadataAndMove(File f,String outputPath,Long hrsOffset,Set<String> subIds,boolean sepYearDir) throws IOException
	{
		String fileName = f.getName();
		String fileExt = Files.getFileExtension(fileName).toLowerCase();
		
		boolean isImage = imageExtensions.contains(fileExt);
		boolean isAudio= audioExtensions.contains(fileExt);
		boolean isVideo = videoExtensions.contains(fileExt);
		
		Metadata metadata = null;
		try(FileInputStream mediaStream = new FileInputStream(f))
		{
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
		}
		
		Directory directory = null; 
		String dateTimeOrigStr = null;
		DateTimeFormatter dtf = isImage ? imageDtf : timezoneDtf;
		int creationTimeTag = ExifDirectoryBase.TAG_DATETIME_ORIGINAL;
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
		        if (isVideo)
		        	if (fileExt.equals("mp4"))
			        	creationTimeTag = Mp4Directory.TAG_CREATION_TIME;
					else if (fileExt.equals("mov"))
						creationTimeTag = QuickTimeMediaDirectory.TAG_CREATION_TIME;
		        else if (isAudio)
		        	creationTimeTag = WavDirectory.TAG_DATE_CREATED;	
		        
	        	dateTimeOrigStr = directory.getString(creationTimeTag);
			}
		}
        
		LocalDateTime mediaTime = null;
		if (dateTimeOrigStr == null)
		{	//use file last modified date if we can't get anything from EXIF
			dateTimeOrigStr = new Date(f.lastModified()).toString();
			if (dateTimeOrigStr == null)
				return;
			mediaTime = LocalDateTime.parse(dateTimeOrigStr,timezoneDtf);
		}
		else
		{
			try
			{
				mediaTime = LocalDateTime.parse(dateTimeOrigStr,dtf);					
			}
			catch (DateTimeParseException dtpe) 
			{
				System.err.println("Can't parse creation time for " + fileName + ": " + dateTimeOrigStr);
				return;
			}
			
			if (hrsOffset != 0l)
				mediaTime = mediaTime.plusHours(hrsOffset);
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
		
		
		if (hrsOffset != 0l)
		{
			try 
			{
				String newDateTime = mediaTime.format(dtf);
				System.out.println("Processing and changing EXIF date of " + f.getPath());
				changeDateTimeOrig(f,movedFile,newDateTime);
				f.delete();
			} 
			catch (Exception e)
			{
				e.printStackTrace();
				System.out.println("Processing " + f.getPath());
				Files.move(f,movedFile);			
			}
		}
		else		
		{
			System.out.println("Processing " + f.getPath());
			Files.move(f,movedFile);
		}
	}	
	
	public String run() throws FileNotFoundException, IOException
	{
		if (msc.getCsvFile() != null)
			parseCsv(msc.getCsvFile());		
		
		String mediaPath = msc.getMediaPath();	
		
		//make output directory inside the provided media folder
		String outputFolderName = OUTPUT_FOLDER_NAME + "_" + new Date().getTime();
		String outputPath = mediaPath + File.separator + outputFolderName;
		File outputDir = new File(outputPath);
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
		
		System.out.println("Searching " + mediaPath + " and subdirectories for media files...");
		boolean sepYearDir = msc.isSepYear();
		int numFiles = eligibleFiles.size();
		int i=1;
		JProgressBar pb = msc.getPb();
		for (File f:eligibleFiles)
		{			
			checkMetadataAndMove(f,outputPath,hrsOffset,subIds,sepYearDir);
			if (pb != null)
				pb.setValue((int) (((i++)/((double)numFiles))*100));
		}
		
		//Move all files out of parent directory we created
		if (!msc.isCreateParentDir())
		{
			System.out.println("Cleaning up...");
			Iterable<File> traverser2 = Files.fileTraverser().depthFirstPreOrder(new File(outputPath));
			for (File f:traverser2)	
			{
				if (!f.exists()) //if we move the directories, the files follow
					continue;
				
				String newPath = StringUtils.remove(f.getAbsolutePath(),outputFolderName + "/");
				if (f.getAbsolutePath().equals(newPath)) //could be the output dir itself
					continue;
				
				Files.move(f,new File(newPath));
			}
			outputDir.delete();
		}
		else
		{
			File finalOutputDir = new File (mediaPath + File.separator + OUTPUT_FOLDER_NAME);
			FileUtils.deleteDirectory(finalOutputDir);			
			outputDir.renameTo(finalOutputDir);
		}
		
		String indexPath = null;
		if (!subIds.isEmpty())
		{
			indexPath = mediaPath + File.separator + "checklistIndex_" + new Date().getTime() + ".csv";
			FileWriter fw = new FileWriter(indexPath);		
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
		
		if (pb != null)
			pb.setValue(100);
		
		System.out.println("ALL DONE! :-)");
		return indexPath;
		
	} //END main
}