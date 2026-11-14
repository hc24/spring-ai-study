package com.hc24.springaidemo.tool;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Component
public class LocalFilesTool implements LocalTool {

    public static final String ROOT_KEY = "localRoot";

    private static final int MAX_LISTING = 200;
    private static final int MAX_SEARCH_DEPTH = 6;
    private static final int MAX_FILES_SCANNED = 2000;
    private static final int MAX_HITS = 40;
    private static final long MAX_SEARCH_FILE_BYTES = 512 * 1024;
    private static final int MAX_READ_CHARS = 16_000;

    @Tool(description = "列出用户已授权目录中某一层的文件和子目录。path 是相对授权目录的路径，空字符串表示授权目录本身")
    public String listDirectory(String path, ToolContext toolContext) {
        Path dir = resolve(path, toolContext);
        if (dir == null) {
            return rejection(path, toolContext);
        }
        if (!Files.isDirectory(dir)) {
            return "不是目录：" + display(dir, toolContext);
        }
        List<String> names = new ArrayList<>();
        try (Stream<Path> children = Files.list(dir)) {
            children.limit(MAX_LISTING).forEach(child -> {
                String marker = Files.isDirectory(child) ? "[目录] " : "[文件] ";
                names.add(marker + child.getFileName());
            });
        } catch (IOException ex) {
            return "无法列出目录：" + ex.getClass().getSimpleName();
        }
        names.sort(Comparator.naturalOrder());
        if (names.isEmpty()) {
            return "目录是空的：" + display(dir, toolContext);
        }
        return String.join("\n", names);
    }

    @Tool(description = "在用户已授权目录中按文本搜索文件内容。path 是相对授权目录的起始路径，query 是要查找的文本")
    public String searchInFiles(String path, String query, ToolContext toolContext) {
        if (query == null || query.isBlank()) {
            return "没有提供要搜索的文本";
        }
        Path start = resolve(path, toolContext);
        if (start == null) {
            return rejection(path, toolContext);
        }
        if (!Files.exists(start)) {
            return "路径不存在：" + display(start, toolContext);
        }
        List<String> hits = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> walk = Files.walk(start, MAX_SEARCH_DEPTH)) {
            var files = walk.filter(Files::isRegularFile).iterator();
            while (files.hasNext() && scanned < MAX_FILES_SCANNED && hits.size() < MAX_HITS) {
                Path file = files.next();
                if (!inside(file, toolContext)) {
                    continue;
                }
                scanned++;
                if (Files.size(file) > MAX_SEARCH_FILE_BYTES) {
                    continue;
                }
                searchFile(file, query, toolContext, hits);
            }
        } catch (IOException ex) {
            return "搜索失败：" + ex.getClass().getSimpleName();
        }
        if (hits.isEmpty()) {
            return "没有找到：" + query;
        }
        return String.join("\n", hits);
    }

    @Tool(description = "读取用户已授权目录中的一个文本文件。path 是相对授权目录的文件路径")
    public String readFile(String path, ToolContext toolContext) {
        Path file = resolve(path, toolContext);
        if (file == null) {
            return rejection(path, toolContext);
        }
        if (!Files.isRegularFile(file)) {
            return "不是文件：" + display(file, toolContext);
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (text.length() > MAX_READ_CHARS) {
                return text.substring(0, MAX_READ_CHARS) + "\n...（已截断）";
            }
            return text;
        } catch (MalformedInputException ex) {
            return "不是可读取的文本文件：" + display(file, toolContext);
        } catch (IOException ex) {
            return "读取失败：" + ex.getClass().getSimpleName();
        }
    }

    private void searchFile(Path file, String query, ToolContext toolContext, List<String> hits) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return;
        }
        String needle = query.toLowerCase();
        for (int i = 0; i < lines.size() && hits.size() < MAX_HITS; i++) {
            if (lines.get(i).toLowerCase().contains(needle)) {
                hits.add(display(file, toolContext) + ":" + (i + 1) + ": " + lines.get(i).strip());
            }
        }
    }

    private static Path resolve(String path, ToolContext toolContext) {
        Path root = grantedRoot(toolContext);
        if (root == null) {
            return null;
        }
        try {
            Path rootReal = root.toRealPath();
            Path candidate = (path == null || path.isBlank() || ".".equals(path))
                    ? root
                    : root.resolve(path).normalize();
            if (!candidate.startsWith(root)) {
                return null;
            }
            // A path can stay under the granted root as text while a symlink resolves outside it.
            if (Files.exists(candidate) && !candidate.toRealPath().startsWith(rootReal)) {
                return null;
            }
            return candidate;
        } catch (IOException ex) {
            return null;
        }
    }

    private static boolean inside(Path file, ToolContext toolContext) {
        return resolve(display(file, toolContext), toolContext) != null;
    }

    private static String rejection(String path, ToolContext toolContext) {
        if (grantedRoot(toolContext) == null) {
            return "用户还没有选择可读取的目录";
        }
        return "拒绝访问授权目录以外的路径：" + (path == null ? "" : path);
    }

    private static Path grantedRoot(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object value = toolContext.getContext().get(ROOT_KEY);
        if (!(value instanceof String root) || root.isBlank()) {
            return null;
        }
        Path path = Path.of(root).toAbsolutePath().normalize();
        return Files.isDirectory(path) ? path : null;
    }

    private static String display(Path path, ToolContext toolContext) {
        Path root = grantedRoot(toolContext);
        if (root == null) {
            return path.toString();
        }
        try {
            return root.relativize(path.toAbsolutePath().normalize()).toString();
        } catch (IllegalArgumentException ex) {
            return path.toString();
        }
    }

}
