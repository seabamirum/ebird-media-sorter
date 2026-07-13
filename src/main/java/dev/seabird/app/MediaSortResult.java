package dev.seabird.app;

import java.nio.file.Path;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Holds the result of the media sort
 */
@NoArgsConstructor
@Getter
@Setter
public class MediaSortResult
{
    /** Path to the generated index file */
    private Path indexPath;
}
