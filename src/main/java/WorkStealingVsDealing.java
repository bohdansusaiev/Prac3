package com.bodgod;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

public class WorkStealingVsDealing {

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        // Task 1: Суми стовпців матриці
        int rows = 5;
        int cols = 10;
        int[][] matrix = generateMatrix(rows, cols, 1, 10);
        System.out.println("Generated Matrix:");
        printMatrix(matrix);

        System.out.println("\n=== Task 1: Sum of Columns ===");
        System.out.println("Using Work Stealing:");
        long workStealingTime1 = workStealingMatrixSum(matrix);
        System.out.println("Using Work Dealing:");
        long workDealingTime1 = workDealingMatrixSum(matrix);

        // Task 2: Пошук файлів за розміром
        System.out.println("\n=== Task 2: File Search ===");
        String directoryPath = "."; // Current directory
        long minSize = 500; // Minimum size in bytes

        System.out.println("Using Work Stealing:");
        long workStealingTime2 = workStealingFileSearch(directoryPath, minSize);
        System.out.println("Using Work Dealing:");
        long workDealingTime2 = workDealingFileSearch(directoryPath, minSize);

        // Summary
        System.out.println("\n=== Summary of Execution Times ===");
        System.out.printf("Task 1 (Matrix Sum): Work Stealing: %d ms, Work Dealing: %d ms%n",
                workStealingTime1, workDealingTime1);
        System.out.printf("Task 2 (File Search): Work Stealing: %d ms, Work Dealing: %d ms%n",
                workStealingTime2, workDealingTime2);
    }

    // Task 1: Work Stealing Approach
    private static long workStealingMatrixSum(int[][] matrix) throws ExecutionException, InterruptedException {
        ForkJoinPool pool = new ForkJoinPool();
        long startTime = System.currentTimeMillis();
        List<Integer> columnSums = pool.invoke(new MatrixColumnSumTask(matrix, 0, matrix[0].length));
        long endTime = System.currentTimeMillis();
        System.out.println("Column Sums: " + columnSums);
        System.out.println("Execution Time: " + (endTime - startTime) + " ms");
        return endTime - startTime;
    }

    // Task 1: Work Dealing Approach
    private static long workDealingMatrixSum(int[][] matrix) throws ExecutionException, InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(matrix[0].length);
        List<Future<Integer>> futures = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        for (int col = 0; col < matrix[0].length; col++) {
            final int column = col;
            futures.add(executor.submit(() -> Arrays.stream(matrix).mapToInt(row -> row[column]).sum()));
        }

        List<Integer> columnSums = new ArrayList<>();
        for (Future<Integer> future : futures) {
            columnSums.add(future.get());
        }
        executor.shutdown();
        long endTime = System.currentTimeMillis();
        System.out.println("Column Sums: " + columnSums);
        System.out.println("Execution Time: " + (endTime - startTime) + " ms");
        return endTime - startTime;
    }

    // Task 2: Work Stealing Approach
    private static long workStealingFileSearch(String directoryPath, long minSize) {
        ForkJoinPool pool = new ForkJoinPool();
        long startTime = System.currentTimeMillis();
        long fileCount = pool.invoke(new FileSearchTask(new File(directoryPath), minSize));
        long endTime = System.currentTimeMillis();
        System.out.println("Files found: " + fileCount);
        System.out.println("Execution Time: " + (endTime - startTime) + " ms");
        return endTime - startTime;
    }

    // Task 2: Work Dealing Approach
    private static long workDealingFileSearch(String directoryPath, long minSize) throws ExecutionException, InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        List<Future<Long>> futures = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        File[] files = new File(directoryPath).listFiles();
        if (files != null) {
            for (File file : files) {
                futures.add(executor.submit(() -> searchFiles(file, minSize)));
            }
        }

        long fileCount = 0;
        for (Future<Long> future : futures) {
            fileCount += future.get();
        }
        executor.shutdown();
        long endTime = System.currentTimeMillis();
        System.out.println("Files found: " + fileCount);
        System.out.println("Execution Time: " + (endTime - startTime) + " ms");
        return endTime - startTime;
    }

    private static long searchFiles(File file, long minSize) {
        if (file.isFile()) {
            return file.length() >= minSize ? 1 : 0;
        } else if (file.isDirectory()) {
            return Arrays.stream(file.listFiles()).mapToLong(f -> searchFiles(f, minSize)).sum();
        }
        return 0;
    }

    private static int[][] generateMatrix(int rows, int cols, int min, int max) {
        Random random = new Random();
        return IntStream.range(0, rows)
                .mapToObj(r -> random.ints(cols, min, max).toArray())
                .toArray(int[][]::new);
    }

    private static void printMatrix(int[][] matrix) {
        for (int[] row : matrix) {
            System.out.println(Arrays.toString(row));
        }
    }
}

// ForkJoin Task for Work Stealing (Matrix)
class MatrixColumnSumTask extends RecursiveTask<List<Integer>> {
    private final int[][] matrix;
    private final int startCol;
    private final int endCol;

    public MatrixColumnSumTask(int[][] matrix, int startCol, int endCol) {
        this.matrix = matrix;
        this.startCol = startCol;
        this.endCol = endCol;
    }

    @Override
    protected List<Integer> compute() {
        if (endCol - startCol <= 2) {
            List<Integer> result = new ArrayList<>();
            for (int col = startCol; col < endCol; col++) {
                int finalCol = col;
                result.add(Arrays.stream(matrix).mapToInt(row -> row[finalCol]).sum());
            }
            return result;
        } else {
            int mid = (startCol + endCol) / 2;
            MatrixColumnSumTask leftTask = new MatrixColumnSumTask(matrix, startCol, mid);
            MatrixColumnSumTask rightTask = new MatrixColumnSumTask(matrix, mid, endCol);
            invokeAll(leftTask, rightTask);
            List<Integer> result = new ArrayList<>(leftTask.join());
            result.addAll(rightTask.join());
            return result;
        }
    }
}

// ForkJoin Task for Work Stealing (File Search)
class FileSearchTask extends RecursiveTask<Long> {
    private final File directory;
    private final long minSize;

    public FileSearchTask(File directory, long minSize) {
        this.directory = directory;
        this.minSize = minSize;
    }

    @Override
    protected Long compute() {
        if (!directory.isDirectory()) {
            return directory.length() >= minSize ? 1L : 0L;
        }

        File[] files = directory.listFiles();
        if (files == null || files.length == 0) return 0L;

        List<FileSearchTask> tasks = new ArrayList<>();
        long count = 0;
        for (File file : files) {
            if (file.isDirectory()) {
                FileSearchTask task = new FileSearchTask(file, minSize);
                tasks.add(task);
                task.fork();
            } else {
                if (file.length() >= minSize) count++;
            }
        }

        for (FileSearchTask task : tasks) {
            count += task.join();
        }

        return count;
    }
}
