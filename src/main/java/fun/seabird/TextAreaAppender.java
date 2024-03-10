package fun.seabird;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import javafx.application.Platform;
import javafx.scene.control.TextArea;

/**
 * A custom appender for logging events that outputs log messages to a {@link TextArea} component.
 * This appender is specifically designed to integrate with JavaFX applications, allowing log messages
 * to be dynamically displayed in a graphical user interface. It extends {@link AppenderBase<ILoggingEvent>}
 * to handle log events and update the UI accordingly.
 * <p>
 * Upon each logging event captured by this appender, it schedules the message to be appended to the
 * designated {@link TextArea} on the JavaFX application thread, ensuring thread safety and consistency
 * of the UI updates. This makes it suitable for real-time logging in JavaFX-based desktop applications.
 * </p>
 * <p>
 * Usage of this appender requires that it be properly initialized and started, typically through configuration
 * in your logging framework setup. Once started, it will capture log events and display them in the TextArea
 * associated with the application's main UI thread.
 * </p>
 */
public class TextAreaAppender extends AppenderBase<ILoggingEvent> 
{
    private TextArea textArea;

    /**
     * Appends the log message from the provided {@link ILoggingEvent} to the {@link TextArea}.
     * This method is called for each log event captured by this appender. The log message is
     * appended to the TextArea on the JavaFX application thread to ensure that UI updates are
     * thread-safe.
     *
     * @param event the logging event containing the message to be appended to the TextArea.
     */
    @Override
    protected void append(ILoggingEvent event) {
        final String message = event.getMessage();
        Platform.runLater(() -> textArea.appendText(message + System.lineSeparator()));
    }

    /**
     * Initializes the TextAreaAppender by associating it with a {@link TextArea} from the
     * {@link MediaSorterApplication}. This method should be called to start the appender
     * and before it begins handling log events. It ensures that the appender is ready to
     * display messages in the designated TextArea.
     */
	@Override
	public void start() 
	{
		this.textArea = MediaSorterApplication.OUTPUT_LOG;
		super.start();
	}
	
}
