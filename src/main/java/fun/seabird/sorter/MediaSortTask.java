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

		JpegImageMetadata jpegMetadata = MediaSortUtils.shouldAdjustExif(originalImageBytes);
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

		String fileExt = MediaSortUtils.getFileExtension(file.getFileName().toString()).toLowerCase();

		boolean isImage = MediaSortUtils.imageExtensions.contains(fileExt);
		boolean isAudio = MediaSortUtils.audioExtensions.contains(fileExt);
		boolean isVideo = MediaSortUtils.videoExtensions.contains(fileExt);

		return isImage || isAudio || isVideo;
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
		if (Files.exists(to)) {
			logger.error(to + " already exists!! Source file left in original location.");
			return from;
		}
		
		if (msc.isUseSymbolicLinks())
			return Files.createSymbolicLink(to, from);
		
		return Files.move(from, to);
	}	
	

	/**

	Transcodes the video file to a smaller size if it exceeds the maximum ML upload size.
	@param file The path to the video file to transcode.
	@throws IOException If an I/O error occurs during the transcoding process.
	*/
	private void transcodeVideo(Path file) throws IOException {
		long fileSizeInBytes = Files.size(file);
		long fileSizeInMB = fileSizeInBytes / (1024 * 1024);
		String fileName = file.getFileName().toString();
		if (fileSizeInMB > MAX_ML_UPLOAD_SIZE_VIDEO) {
			String outputFileName = fileName.replaceFirst("[.][^.]+$", "") + TRANSCODED_VIDEO_SUFFIX + ".mp4";
			Path outputFile = file.getParent().resolve(outputFileName);
			if (Files.notExists(outputFile)) {
				logger.info("{} too large for ML upload, transcoding with ffmpeg...",fileName);
				String convVideoPath = outputFile.toString();
				String[] command = { "ffmpeg", "-i", file.toString(), "-map_metadata", "0:s:0", "-c:v", "libx264",
						"-crf", "22", "-preset", "medium", "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart",
						"-max_muxing_queue_size", "1024", convVideoPath };

				ProcessBuilder pb = new ProcessBuilder(command);
				pb.redirectError(ProcessBuilder.Redirect.DISCARD);
				pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);

				try {
					process = pb.start();
					int res = process.waitFor();
					if (res == 0)
						logger.info("Saved converted video to {}", convVideoPath);
				} catch (InterruptedException | IOException e) {
					logger.error("Cannot transcode video to smaller size", e);
				} finally {
					if (process != null) {
						process.destroy();
					}
				}
			}
		}
	}
		
	/**
	 * @param file
	 * @param hrsOffset
	 * @return a LocalDateTime for the media file, never null
	 * @throws IOException
	 */
	private static LocalDateTime findCreationDt(Path file,Long hrsOffset) throws IOException
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
	private Path checkMetadataAndMove(Path file, Path outputDir, Long hrsOffset,boolean sepYearDir,FolderGroup folderGroup) throws IOException {
		
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
	
	private void afterMove(Path file,Long hrsOffset) throws IOException
	{
		String fileName = file.getFileName().toString();
		String fileExt = MediaSortUtils.getFileExtension(fileName).toLowerCase();
		if (msc.isTranscodeVideos() && fileExt.equals("mp4") && !fileName.contains(TRANSCODED_VIDEO_SUFFIX))
		{
			transcodeVideo(file);
			return;
		}
		
		boolean isImage = MediaSortUtils.imageExtensions.contains(fileExt);		
		if (isImage && hrsOffset != 0l) {
			String newDateTime = findCreationDt(file,hrsOffset).format(imageDtf);
			if (changeDateTimeOrig(file, newDateTime)) {
				logger.info("Changed EXIF date of {} to {}",file.getFileName(),newDateTime);
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

		Long hrsOffset = msc.getHrsOffset();
		
		logger.info("Analyzing files...");
		List<Path> eligibleFiles = new ArrayList<>();
		try (Stream<Path> stream = Files.walk(mediaPath)) {		    
		    stream.filter(MediaSortTask::isEligibleMediaFile)
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
			afterMove(f,hrsOffset);

		// Move all files out of temp parent directory we created
		if (!msc.isCreateParentDir()) {
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
		} else {
			Path finalOutputDir = mediaPath.resolve(MediaSortUtils.OUTPUT_FOLDER_NAME);
			if (Files.exists(finalOutputDir))
				logger.error("Directory {} already exists! Check {} for results.",finalOutputDir,outputDir);
			else
				Files.move(outputDir, finalOutputDir, StandardCopyOption.ATOMIC_MOVE);
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