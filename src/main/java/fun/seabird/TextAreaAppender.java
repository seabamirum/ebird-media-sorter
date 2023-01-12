package fun.seabird;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import javafx.application.Platform;
import javafx.scene.control.TextArea;

public class TextAreaAppender extends AppenderBase<ILoggingEvent> 
{
    private TextArea textArea;

    @Override
    protected void append(ILoggingEvent event) {
        final String message = event.getMessage();
        Platform.runLater(() -> textArea.appendText(message + System.lineSeparator()));
    }

	@Override
	public void start() 
	{
		this.textArea = MediaSorterApplication.OUTPUT_LOG;
		super.start();
	}
	
}
