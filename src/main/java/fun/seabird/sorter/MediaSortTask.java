package fun.seabird.sorter;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import java.util.SequencedSet;
import java.util.TreeSet;
import java.util.stream.Gatherers;
import java.util.stream.Stream;

import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;

import de.siegmar.fastcsv.writer.CsvWriter;
import fun.seabird.EbirdCsvParser;
import fun.seabird.EbirdCsvParser.PreSort;
import fun.seabird.EbirdCsvRow;
import fun.seabird.provider.CreationDateProvider;
import fun.seabird.provider.ExifCreationDateProvider;
import fun.seabird.provider.FileModifiedCreationDateProvider;
import fun.seabird.provider.FileNameCreationDateProvider;
import fun.seabird.util.MediaSortUtils;
import javafx.concurrent.Task;

public class MediaSortTask extends Task<Path> {
		
	private static final Logger logger = LoggerFactory.getLogger(MediaSortTask.class);
	private static final DateTimeFormatter imageDtf = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");
	private static final DateTimeFormatter folderDtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");	

	private static final int LOGGING_WINDOW_SIZE = 100;
	private static final long MAX_ML_UPLOAD_SIZE_VIDEO = 1000l;
	private static final String TRANSCODED_VIDEO_SUFFIX = "_s";

	private static final String[] invalidChars = new String[] { " ", ":", ",", ".", "/", "\\", ">", "<" };
	private static final String[] validChars = new String[] { "-", "--", "-", "-", "-", "-", "-", "-" };

	private static final List<CreationDateProvider> creationDateProviders = List.of(new ExifCreationDateProvider(),
			new FileNameCreationDateProvider(), new FileModifiedCreationDateProvider());	
	
	//eBird CSV fields
	private static final RangeMap<LocalDateTime, String> rangeMap = TreeRangeMap.create();
	private static final SequencedMap<String, SubStats> checklistStatsMap = new LinkedHashMap<>();
	private static final SequencedSet<String> subIds = new TreeSet<>();
	
	private record FileInfo(String name, String base, String ext) {
	    FileInfo(Path file) {
	        this(file.getFileName().toString(),
	             MediaSortUtils.getBaseName(file.getFileName().toString()),
	             MediaSortUtils.getFileExtension(file.getFileName().toString()).toLowerCase());
	    }
	}
	
	private final MediaSortCmd msc;
	
	private transient Process process;	

	public MediaSortTask(MediaSortCmd msc) {
		this.msc = msc;
	}

	/**
	 * Parses a CSV record and updates the checklist statistics map and range map
	 * with relevant information.
	 * 
	 * @param record The CSV record to be parsed.
	 */
	private void parseCsvLine(EbirdCsvRow row) 
	{
		int duration = row.getDuration();
		if (duration <= 0 || row.getTime() == null)
			return;

		String subId = row.getSubId();
		checklistStatsMap.computeIfAbsent(subId, _ ->
		{
			var subBeginTime = row.dateTime();
			var subEndTime = subBeginTime.plusMinutes(duration);

			rangeMap.put(Range.closed(subBeginTime, subEndTime), subId);
			return new SubStats(subBeginTime,row.getSubnat1Code(),row.getSubnat2Name(),row.getLocName());
		});

		var assetIds = row.getAssetIds();
		if (!assetIds.isEmpty())
			checklistStatsMap.get(subId).incNumAssetsUploaded(assetIds.size());			
	}
	
	private static boolean changeDateTimeOrig(Path imageFile, String newDateTime) throws IOException {
		byte[] originalImageBytes = Files.readAllBytes(imageFile);

		JpegImageMetadata jpegMetadata = MediaSortUtils.jpegImageMetadata(originalImageBytes);
		if (jpegMetadata == null)
			return false;

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
			TiffImageMetadata exif = jpegMetadata.getExif();
			TiffOutputSet outputSet = exif != null ? exif.getOutputSet() : new TiffOutputSet();
			TiffOutputDirectory exifDirectory = outputSet.getOrCreateExifDirectory();

			exifDirectory.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL);
			exifDirectory.add(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL, newDateTime);

			new ExifRewriter().updateExifMetadataLossless(originalImageBytes, baos, outputSet);

			try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(imageFile))) {
				os.write(baos.toByteArray());
			}

		return true;
	}

	private static boolean isEligibleMediaFile(Path file) {
		if (Files.isDirectory(file) || Files.isSymbolicLink(file))
			return false;

		return MediaSortUtils.mediaExtensions.contains(MediaSortUtils.getFileExtension(file).toLowerCase());
	}

	/**
	 * Moves a file from the source path to the destination path, optionally using a symbolic link.
	 * <p>
	 * If the destination file already exists, the operation is aborted, an error is logged, and the
	 * source path is returned unchanged. Depending on the configuration in {@code msc}, this method
	 * either creates a symbolic link at the destination pointing to the source, or physically moves
	 * the file from the source to the destination.
	 * </p>
	 *
	 * @param from the source path of the file to move
	 * @param to the destination path where the file should be moved or linked
	 * @return the path of the file after the operation: {@code from} if the move fails due to an
	 *         existing destination, or {@code to} if the move or link creation succeeds
	 * @throws IOException if an I/O error occurs during the move or symbolic link creation
	 */
	private Path moveFile(Path from, Path to) throws IOException 
	{
		if (Files.exists(to)) 
		{			
			if (Files.size(from) != Files.size(to))
			{
				logger.warn(to + " already exists and is likely different!! Source file left in original location.");
				return from;
			}
			
			Files.delete(from);
			return to;
		}
		
		if (msc.isUseSymbolicLinks())
			return Files.createSymbolicLink(to, from);
		
		return Files.move(from, to);
	}		

	private static boolean runFfmpeg(String[] command, String operation) 
	{
	    ProcessBuilder pb = new ProcessBuilder(command);
	    pb.redirectError(ProcessBuilder.Redirect.DISCARD);
	    pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
	    Process process = null;
	    try {
	        process = pb.start();
	        int res = process.waitFor();
	        if (res != 0) {
	            logger.warn("FFmpeg {} failed with exit code {}", operation, res);
	            return false;
	        }
	        return true;
	    } catch (InterruptedException | IOException e) {
	        logger.error("Cannot perform FFmpeg {}", operation, e);
	        return false;
	    } finally {
	        if (process != null) process.destroy();
	    }
	}
		
	/**
	 * @param file
	 * @param hrsOffset
	 * @return a LocalDateTime for the media file, never null
	 * @throws IOException
	 */
	private static LocalDateTime findCreationDt(Path file,long hrsOffset) throws IOException
	{
		LocalDateTime mediaTime = null;
		for (CreationDateProvider cdp : creationDateProviders) {
			mediaTime = cdp.findCreationDate(file, hrsOffset);
			if (mediaTime != null)
				break;
		}
		
		return mediaTime;
	}
	
	/**
	 * Calculates the destination directory path based on output directory, submission ID, media timestamp, and folder grouping strategy.
	 * 
	 * @param outputDir   the base output directory where the calculated path will be resolved, must not be {@code null}
	 * @param subId       the submission ID, may be {@code null}; if {@code null}, the path excludes submission-specific details
	 * @param mediaTime   the timestamp of the media, used to generate a date string, must not be {@code null}
	 * @param folderGroup the grouping strategy for organizing folders (e.g., by location or date), must not be {@code null}
	 * @return the resolved {@link Path} representing the destination directory
	 */
	private static Path calcDestDir(Path outputDir, String subId, LocalDateTime mediaTime, FolderGroup folderGroup) {
	    
		String mediaDateStr = mediaTime.format(folderDtf);
	    
	    if (subId == null)
	        return outputDir.resolve(mediaDateStr);

	    SubStats ss = checklistStatsMap.get(subId);
	    String locNameAbbrev = StringUtils.abbreviate(ss.getLocName(), "", 40);
	    locNameAbbrev = StringUtils.replaceEach(locNameAbbrev, invalidChars, validChars);

	    Path destDir = switch (folderGroup) {
	        case location -> outputDir
	                .resolve(ss.getSubnational1Code())
	                .resolve(ss.getCounty() != null ? ss.getCounty() : "")
	                .resolve(locNameAbbrev)
	                .resolve(mediaDateStr + "_" + subId);
	        case date -> outputDir
	                .resolve(mediaDateStr)
	                .resolve(ss.getSubnational1Code() + "_" + ss.getCounty() + "_" + locNameAbbrev + "_" + subId);
	        default -> outputDir.resolve(mediaDateStr + "_" + subId);
	    };

	    subIds.add(subId);
	    ss.incNumAssetsLocal();

	    return destDir;
	}

	/**
	 * Checks the metadata of a file and moves it to the appropriate directory based on the metadata information.
	 *
	 * @param file         The file to check and move.
	 * @param outputDir    The output directory containing the folder(s) where the file will be moved.
	 * @param hrsOffset    The hour offset for adjusting creation date.
	 * @param sepYearDir   Flag indicating whether to separate files into year directories.
	 * @param folderGroup  The folder grouping mode.
	 * @throws IOException If an I/O error occurs while performing the operation.
	 */
	private Path checkMetadataAndMove(Path file, Path outputDir, long hrsOffset,boolean sepYearDir,FolderGroup folderGroup) throws IOException {
		
		final LocalDateTime mediaTime = findCreationDt(file,hrsOffset);

		Path grandParentDir = outputDir;
		if (sepYearDir)
			grandParentDir = grandParentDir.resolve(Path.of(String.valueOf(mediaTime.getYear())));

		String subId = rangeMap.get(mediaTime);
		
		Path destDir = calcDestDir(grandParentDir, subId, mediaTime, folderGroup);

		Files.createDirectories(destDir);

		String fileName = file.getFileName().toString();
		Path destFile = destDir.resolve(fileName);
		
		return moveFile(file, destFile);
	}
	
	/**
	 * @param dir
	 * @throws IOException
	 */
	private static void cleanEmptyDirectories(Path dir) throws IOException {
	    if (Files.isDirectory(dir)) {
	        List<Path> subDirectories;
	        try (Stream<Path> stream = Files.list(dir)) {
	            subDirectories = stream.filter(Files::isDirectory).toList();
	        }

	        for (Path subDir : subDirectories) {
	            cleanEmptyDirectories(subDir);
	        }

	        List<Path> files;
	        try (Stream<Path> stream = Files.list(dir)) {
	            files = stream.toList();
	        }

	        if (files.isEmpty()) {
	            Files.deleteIfExists(dir);
	            logger.info("Deleted empty directory {}", dir);
	        }
	    }
	}
	
	private Path shouldConvertVideo(Path file, FileInfo info) throws IOException {
	    if (!msc.isTranscodeVideos() || info.name().endsWith(TRANSCODED_VIDEO_SUFFIX) ||
	        !MediaSortUtils.videoExtensions.contains(info.ext())) {
	        return null;
	    }
	    Path output = file.getParent().resolve(info.base() + TRANSCODED_VIDEO_SUFFIX + ".mp4");
	    if (Files.exists(output)) return null;
	    long sizeMB = Files.size(file) / (1024 * 1024);
	    boolean isMovOrAvi = info.ext().equals("avi") || info.ext().equals("mov");
	    boolean tooLarge = sizeMB > MAX_ML_UPLOAD_SIZE_VIDEO;
	    return (isMovOrAvi || tooLarge) ? output : null;
	}

	private Path shouldExtractAudio(Path file, FileInfo info) {
	    if (!msc.isExtractAudio() || !MediaSortUtils.videoExtensions.contains(info.ext())) {
	        return null;
	    }
	    Path output = file.getParent().resolve(info.base() + ".mp3");
	    return Files.exists(output) ? null : output;
	}

	private void afterMove(Path file) throws IOException {
	    FileInfo info = new FileInfo(file);

	    Path converted = shouldConvertVideo(file, info);
	    if (converted != null) {
	        logger.info("{} transcoding to MP4 with ffmpeg...", info.name());
	        if (runFfmpeg(new String[]{"ffmpeg", "-threads", "1", "-i", file.toString(), "-map_metadata", "0", "-c:v", "libx264", "-threads", "2", "-crf", "22", "-preset", "medium", "-c:a", "copy", converted.toString()}, "video transcoding")) {
	            logger.info("Saved converted video to {}", converted.getFileName());
	        }
	    }

	    Path extracted = shouldExtractAudio(file, info);
	    if (extracted != null) {
	        logger.info("Extracting audio from {} to MP3 with ffmpeg...", info.name());
	        if (runFfmpeg(new String[]{"ffmpeg", "-i", file.toString(), "-vn", "-c:a", "mp3", "-b:a", "192k","-map_metadata","0", extracted.toString()}, "audio extraction")) {
	            logger.info("Saved extracted audio to {}", extracted.getFileName());
	        }
	    }

	    if (msc.getHrsOffset() != 0L && ("jpg".equals(info.ext()) || "jpeg".equals(info.ext())))
	    {
	        String newDt = findCreationDt(file, msc.getHrsOffset()).format(imageDtf);
	        if (changeDateTimeOrig(file, newDt)) {
	            logger.info("Changed EXIF date of {} to {}", file.getFileName(), newDt);
	        }
	    }
	}
	
	@SuppressWarnings("resource")
	private static Path writeResults(Path mediaPath)
	{
		if (subIds.isEmpty())
			return null;
		
		Path resultsFile = mediaPath.resolve("checklistIndex_" + Instant.now().toEpochMilli() + ".csv");
		
        try (CsvWriter csvWriter = CsvWriter.builder().build(resultsFile,StandardCharsets.UTF_8,StandardOpenOption.CREATE))
        {
        	csvWriter.writeRecord("Checklist Link", "Date", "State", "County", "Num Uploaded Assets", "Num Local Assets");
        	
            for (String subId : subIds) {
                SubStats ss = checklistStatsMap.get(subId);                
                csvWriter.writeRecord("https://ebird.org/checklist/" + subId + "/media",ss.getDate(), ss.getSubnational1Code(), ss.getCounty(),String.valueOf(ss.getNumAssetsUploaded()),String.valueOf(ss.getNumAssetsLocal()));
            }
        } catch (IOException e) {
            logger.error("Error writing CSV!",e);
        }
	        
	    return resultsFile;
	}

	/**
	 * Executes the main logic of the media processing task. This method is called
	 * when the task is executed.
	 * 
	 * @return The path to the generated index file, or null if no eligible media
	 *         files are found.
	 * @throws Exception If an error occurs during the execution of the task.
	 */
	@Override
	protected Path call() throws Exception {
		if (msc.getCsvFile() != null && msc.isReParseCsv())
		{
			rangeMap.clear();
			checklistStatsMap.clear();
			subIds.clear();
			
			EbirdCsvParser.parseCsv(msc.getCsvFile(),this::parseCsvLine,PreSort.NONE);
			
			msc.setReParseCsv(false);			
		}

		Path mediaPath = msc.getMediaPath();

		// make output directory inside the provided media folder
		String outputDirName = MediaSortUtils.OUTPUT_FOLDER_NAME + "_" + Instant.now().toEpochMilli();
		Path outputDir = mediaPath.resolve(outputDirName);

		long hrsOffset = msc.getHrsOffset();
		
		logger.info("Analyzing files...");
		List<Path> eligibleFiles = new ArrayList<>();
		try (Stream<Path> stream = Files.walk(mediaPath)) {		    
		    stream.filter(MediaSortTask::isEligibleMediaFile)
		    	.sorted(Comparator.comparing(Path::getFileName))
		        .gather(Gatherers.windowFixed(LOGGING_WINDOW_SIZE))
		        .peek(window -> logger.info("Added 100 files to processing queue ({} total)...",(eligibleFiles.size() + window.size())))
		        .forEach(eligibleFiles::addAll);
		}

		if (eligibleFiles.isEmpty()) {
			logger.info("No eligible media files found.");
			updateProgress(1.0, 1.0);
			return null;
		}

		boolean sepYearDir = msc.isSepYear();
		int numFiles = eligibleFiles.size();
		int j = 1;
		logger.info("Processing {} files in {} and subdirectories...",numFiles,mediaPath);
		List<Path> movedFiles = new ArrayList<>(numFiles);
		for (Path f : eligibleFiles) {
			
			Path movedFile = checkMetadataAndMove(f, outputDir, hrsOffset, sepYearDir, msc.getFolderGroup());
			
			movedFiles.add(movedFile);
			
			final double progPer = j++ / ((double) numFiles);
			updateProgress(progPer, 1.0);
		}
		
		if (hrsOffset != 0l)
			logger.info("Adjusting EXIF data (may take a while)...");
		else		
			logger.info("Finishing up...");
		
		for (Path f : movedFiles)
			afterMove(f);

		if (msc.isCreateSubDir()) {
			Path finalOutputDir = mediaPath.resolve(MediaSortUtils.OUTPUT_FOLDER_NAME);
			if (Files.exists(finalOutputDir))
				logger.error("Directory {} already exists! Check {} for results.",finalOutputDir,outputDir);
			else
				Files.move(outputDir, finalOutputDir, StandardCopyOption.ATOMIC_MOVE);
		} else {
			List<Path> dirToMove = new ArrayList<>();
			   try (Stream<Path> stream = Files.walk(outputDir, 1)) {
			        stream.filter(path -> !path.equals(outputDir))
			              .forEach(dirToMove::add);
			    }

			for (Path directory : dirToMove) {
				if (!Files.exists(directory))
					continue;

				Path newPath = mediaPath.resolve(outputDir.relativize(directory));
				Files.move(directory, newPath, StandardCopyOption.ATOMIC_MOVE);
			}

			Files.delete(outputDir);
		}		
		
		Path resultsFile = writeResults(mediaPath);
        
        logger.info("Deleting empty directories...");
        cleanEmptyDirectories(mediaPath);
        
		updateProgress(1.0, 1.0);

		logger.info("ALL DONE! :-)");
		return resultsFile;

	}

	public Process getProcess() {
		return process;
	}

}