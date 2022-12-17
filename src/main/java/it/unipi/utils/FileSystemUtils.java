package it.unipi.utils;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.tartarus.snowball.ext.englishStemmer;

import static java.lang.Math.log;

public final class FileSystemUtils {

    public static void setupEnvironment(){
        try {
            for(String directory: Constants.DIRECTORIES_PATHS)
                Files.createDirectories(Paths.get(directory));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void deleteTemporaryFolders(){
        for(String directory: Constants.TEMPORARY_DIRECTORIES_PATHS) {
            Path pathToBeDeleted = Paths.get(directory);
            try (Stream<Path> files = Files.walk(pathToBeDeleted)) {
                files.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}