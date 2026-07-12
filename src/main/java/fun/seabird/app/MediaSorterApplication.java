package fun.seabird.app;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ResourceBundle;

import fun.seabird.sorter.FolderGroup;
import fun.seabird.sorter.MediaSortCmd;
import fun.seabird.sorter.MediaSortResult;
import fun.seabird.sorter.MediaSortTask;
import fun.seabird.util.MediaSortUtils;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;

/**
 * JavaFX entry point for the Media Sorter application.
 *
 * <p>Presents a single-window GUI that lets the user configure and run a
 * {@link MediaSortTask}. The window exposes the following controls:
 *
 * <ul>
 *   <li><b>Source folder</b> – directory containing the media files to sort.</li>
 *   <li><b>CSV file</b> – optional location CSV used to enrich sort metadata.</li>
 *   <li><b>Sub-directory</b> – whether to write output into a dedicated
 *       sub-folder inside the source directory.</li>
 *   <li><b>Separate year directories</b> – split output by year as well as
 *       date/location.</li>
 *   <li><b>Location sort</b> – group files by location instead of date.</li>
 *   <li><b>Symbolic links</b> – create symbolic links rather than copying
 *       files.</li>
 *   <li><b>Transcode videos</b> – re-encode video files during the sort.</li>
 *   <li><b>Extract audio</b> – strip audio tracks from video files.</li>
 *   <li><b>EXIF offset slider</b> – adjust all EXIF timestamps by ±6 hours
 *       to correct for timezone or camera-clock drift.</li>
 * </ul>
 *
 * <p>UI strings are loaded from the {@code ui} {@link ResourceBundle}, allowing
 * the interface to be localised without recompiling.
 *
 * <p>Once the task completes successfully a "Results" button becomes available;
 * clicking it opens the generated index file using the platform's default
 * application via {@link Desktop#open(File)}.
 *
 * <p>A JVM shutdown hook is registered to cleanly terminate any external
 * process spawned by the {@link MediaSortTask} if the application is closed
 * while sorting is in progress.
 */
public class MediaSorterApplication extends Application
{
    /** Width of the application window in pixels. */
    private static final int FRAME_WIDTH = 650;

    /** Height of the application window in pixels. */
    private static final int FRAME_HEIGHT = 800;

    /** Base name of the {@link ResourceBundle} that supplies all UI strings. */
    private static final String UI_PROPERTIES_FILE_BASE = "ui";

    /** File-chooser filter that restricts selection to {@code *.csv} files. */
    private static final ExtensionFilter csvFilter = new ExtensionFilter("CSV Files", "*.csv");

    /**
     * Shared, append-only log area written to by the running {@link MediaSortTask}.
     * Declared {@code public} so that the task can append progress messages
     * without holding a reference to the full application instance.
     */
    public static final TextArea OUTPUT_LOG = new TextArea();
    
    private MediaSortTask task;

    /**
     * Launches a {@link MediaSortTask} in a background daemon thread and wires
     * up all related UI state transitions.
     *
     * <p>On invocation the Run button and Results button are disabled, the
     * progress bar and scroll pane become visible, and the output log is
     * cleared. The progress bar is bound to the task's
     * {@link javafx.concurrent.Task#progressProperty() progressProperty} for
     * the duration of the run.
     *
     * <p>When the task succeeds:
     * <ul>
     *   <li>The Run button is re-enabled.</li>
     *   <li>If the task returns a non-{@code null} index path and
     *       {@link Desktop} is supported, the Results button is enabled and
     *       made visible.</li>
     * </ul>
     *
     * <p>A JVM shutdown hook is registered to {@link Process#waitFor() wait}
     * for and then {@link Process#destroy() destroy} any external process
     * associated with the task, preventing orphaned child processes when the
     * application exits mid-sort.
     *
     * @param runBut the button that triggers the sort; disabled for the
     *               duration of the task and re-enabled on completion
     * @param resBtn the button that opens the sort results; hidden until the
     *               task succeeds and produces a valid index path
     * @param pb     the progress bar bound to the task's progress property
     * @param scroll the scroll pane wrapping {@link #OUTPUT_LOG}; made visible
     *               when the task starts
     * @param msc    the sort configuration (source path, offsets, flags, etc.)
     *               passed directly to the new {@link MediaSortTask}
     * @param msr    the result holder whose index path is set once the task
     *               completes successfully
     */
    private void runMediaSortTask(Button runBut, Button resBtn, ProgressBar pb,
            ScrollPane scroll, MediaSortCmd msc, MediaSortResult msr)
    {
        runBut.setDisable(true);
        resBtn.setDisable(true);
        resBtn.setVisible(false);
        pb.setVisible(true);
        scroll.setVisible(true);

        OUTPUT_LOG.clear();

        task = new MediaSortTask(msc);

        pb.progressProperty().bind(task.progressProperty());

        Thread taskThr = new Thread(task);
        taskThr.setDaemon(true);
        taskThr.start();

        task.setOnSucceeded(_ -> {
            Path path = task.getValue();
            runBut.setDisable(false);

            msr.setIndexPath(path);
            if (msr.getIndexPath() != null && Desktop.isDesktopSupported()) {
                resBtn.setDisable(false);
                resBtn.setVisible(true);
            }
        });    
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (task.currentProcess() != null) {
                task.destroyCurrentProcess();
            }
        }));
    }

    /**
     * Builds and displays the primary application window.
     *
     * <p>All UI controls are created and laid out in a {@link GridPane}.
     * Event handlers are attached inline and delegate to {@link #runMediaSortTask}
     * or update the shared {@link MediaSortCmd} configuration object directly.
     *
     * <p>The window title and all visible labels are sourced from the
     * {@code ui} {@link ResourceBundle}.
     *
     * @param s the primary {@link Stage} provided by the JavaFX runtime
     * @throws Exception if the scene graph cannot be constructed or the
     *                   resource bundle cannot be loaded
     */
    @Override
    public void start(Stage s) throws Exception
    {
        ResourceBundle msgs = ResourceBundle.getBundle(UI_PROPERTIES_FILE_BASE);

        s.setTitle(msgs.getString("titleText"));

        final MediaSortCmd msc = new MediaSortCmd();
        final MediaSortResult msr = new MediaSortResult();

        Label introLbl = new Label(msgs.getString("introText"));

        Button browseBut = new Button(msgs.getString("browseBtnText"));
        Label browseButLbl = new Label();

        Label offsetLbl = new Label(msgs.getString("exifAdjText"));
        Slider offsetSlider = new Slider(-6, 6, 0);
        offsetSlider.setSnapToTicks(true);
        offsetSlider.setMajorTickUnit(1);

        Button csvBrowse = new Button(msgs.getString("csvBtnText"));
        Label csvBrowseLbl = new Label();

        CheckBox locSortCb = new CheckBox(msgs.getString("locSortText"));
        CheckBox sepYearDirCb = new CheckBox(msgs.getString("sepYearText"));
        CheckBox subDirCb = new CheckBox(msgs.getString("subDirText"));
        subDirCb.setSelected(true);
        CheckBox symbLinkCb = new CheckBox(msgs.getString("symbLinkText"));
        CheckBox transcodeVidCb = new CheckBox(msgs.getString("transcodeVidText"));
        CheckBox extractAudioCb = new CheckBox(msgs.getString("extractAudioText"));

        Button runBut = new Button(msgs.getString("runBtnText"));
        runBut.setDisable(true);

        ProgressBar pb = new ProgressBar(0.0);
        pb.setVisible(false);

        OUTPUT_LOG.setWrapText(true);
        OUTPUT_LOG.setEditable(false);
        ScrollPane scroll = new ScrollPane(OUTPUT_LOG);
        scroll.setVisible(false);

        Button resBtn = new Button(msgs.getString("resBtnText"));
        resBtn.setDisable(true);
        resBtn.setVisible(false);

        browseBut.setOnAction(_ ->
            {
                DirectoryChooser dc = new DirectoryChooser();

                File selectedFile = dc.showDialog(browseBut.getScene().getWindow());

                if (selectedFile != null)
                {
                    String path = selectedFile.getPath();
                    msc.setMediaPath(selectedFile.toPath());
                    browseButLbl.setText(path);
                    runBut.setDisable(false);
                    subDirCb.setText(msgs.getString("subDirText") + " " + msc.getMediaPath().resolve(MediaSortUtils.OUTPUT_FOLDER_NAME));
                }
            }
        );

        offsetSlider.valueProperty().addListener((_, _, newValue) ->
        {
            int offset = newValue.intValue();
            msc.setHrsOffset(Long.valueOf(offset));

            if (offset != 0)
                offsetLbl.setText("EXIF Adjustment (" + offset + " hours)");
            else
                offsetLbl.setText(msgs.getString("exifAdjText"));

            offsetSlider.setValue(newValue.doubleValue());
        });

        csvBrowse.setOnAction(_ ->
        {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(csvFilter);

            File f = fc.showOpenDialog(csvBrowse.getScene().getWindow());

            if (f != null)
            {
                msc.setReParseCsv(true);
                msc.setCsvFile(f.toPath());
                csvBrowseLbl.setText(f.getPath());
            }
        });

        sepYearDirCb.setOnAction(_ ->
            {
                msc.setSepYear(sepYearDirCb.isSelected());
            }
        );

        locSortCb.setOnAction(_ ->
            {
                if (locSortCb.isSelected())
                    msc.setFolderGroup(FolderGroup.location);
                else
                    msc.setFolderGroup(FolderGroup.date);
            }
        );

        subDirCb.setOnAction(_ ->
            {
                msc.setCreateSubDir(subDirCb.isSelected());
            }
        );

        symbLinkCb.setOnAction(_ ->
        {
            msc.setUseSymbolicLinks(symbLinkCb.isSelected());
        });

        transcodeVidCb.setOnAction(_ ->
        {
            msc.setTranscodeVideos(transcodeVidCb.isSelected());
        });

        extractAudioCb.setOnAction(_ ->
        {
            msc.setExtractAudio(extractAudioCb.isSelected());
        });

        runBut.setOnAction(_ -> runMediaSortTask(runBut, resBtn, pb, scroll, msc, msr));

        resBtn.setOnAction(_ ->
        {
            Desktop desktop = Desktop.getDesktop();
            File resFile = new File(msr.getIndexPath().toUri());
            if (resFile.exists())
                new Thread(() -> {
                    try {
                        desktop.open(resFile);
                    } catch (IOException e) {
                        System.err.println(e);
                    }
                }).start();
            }
        );

        GridPane gp = new GridPane();

        gp.setVgap(20.0);

        gp.add(introLbl, 0, 0, 3, 1);
        gp.add(new HBox(10, browseBut, browseButLbl), 0, 1, 3, 1);

        gp.add(new HBox(10, csvBrowse, csvBrowseLbl), 0, 2, 3, 1);

        gp.add(subDirCb, 0, 3, 3, 1);
        gp.add(sepYearDirCb, 0, 4, 3, 1);
        gp.add(locSortCb, 0, 5, 3, 1);
        gp.add(symbLinkCb, 0, 6, 3, 1);
        gp.add(transcodeVidCb, 0, 7, 3, 1);
        gp.add(extractAudioCb, 0, 8, 3, 1);

        gp.add(new HBox(10, offsetSlider, offsetLbl), 0, 9, 3, 1);

        gp.add(new Separator(), 0, 10, 3, 1);

        gp.add(runBut, 0, 11, 3, 1);

        gp.add(pb, 0, 12, 3, 2);

        gp.add(scroll, 0, 14, 3, 1);
        gp.add(resBtn, 0, 18, 3, 1);

        Scene scene = new Scene(gp, FRAME_WIDTH, FRAME_HEIGHT);
        Insets padding = new Insets(10, 0, 10, 30);
        ((Region) scene.getRoot()).setPadding(padding);
        s.setScene(scene);
        s.setResizable(true);
        s.show();
    }    
    
    @Override
    public void stop() throws Exception {
        if (task != null) {
            task.destroyCurrentProcess();
        }
        super.stop();
    }
}