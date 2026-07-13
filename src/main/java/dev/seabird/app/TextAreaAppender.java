package dev.seabird.app;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import javafx.application.Platform;
import javafx.scene.control.TextArea;

/**
 * Custom Logback appender that outputs log messages to a JavaFX {@link TextArea}.
 * Updates are performed on the JavaFX Application Thread for thread safety.
 */
public class TextAreaAppender extends AppenderBase<ILoggingEvent> 
{
    private TextArea textArea;
   
    @Override
    protected void append(ILoggingEvent event) {
        final String message = event.getFormattedMessage();
        Platform.runLater(() -> textArea.appendText(message + System.lineSeparator()));
    }
   
	@Override
	public void start() 
	{
		this.textArea = MediaSorterApplication.OUTPUT_LOG;
		super.start();
	}	
}
