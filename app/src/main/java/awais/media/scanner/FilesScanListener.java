package awais.media.scanner;

import java.io.File;
import java.util.Stack;

public interface FilesScanListener {
    void failedFiles(final Stack<File> fileStack);
    void itemsLeft(final int count, final int failedSize);
}