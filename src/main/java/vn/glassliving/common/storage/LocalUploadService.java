package vn.glassliving.common.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.glassliving.common.exception.BusinessException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class LocalUploadService {

    private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final Path uploadRoot;

    public LocalUploadService(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public String storeImage(MultipartFile file, String folder, String prefix) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw BusinessException.badRequest("Ảnh tải lên không được vượt quá 10MB.");
        }
        String contentType = Objects.toString(file.getContentType(), "").toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("image/")) {
            throw BusinessException.badRequest("Chỉ hỗ trợ file ảnh JPG, PNG hoặc WEBP.");
        }

        String extension = resolveExtension(file.getOriginalFilename(), contentType);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw BusinessException.badRequest("Định dạng ảnh không hợp lệ. Hãy dùng JPG, PNG hoặc WEBP.");
        }

        validateReadableImage(file, extension);

        String safeFolder = safeFolder(folder);
        Path directory = uploadRoot.resolve(safeFolder).normalize();
        if (!directory.startsWith(uploadRoot)) {
            throw BusinessException.badRequest("Thư mục upload không hợp lệ.");
        }

        String filename = safeSegment(prefix) + "-" + UUID.randomUUID() + "." + extension;
        Path target = directory.resolve(filename).normalize();
        if (!target.startsWith(directory)) {
            throw BusinessException.badRequest("Tên file upload không hợp lệ.");
        }

        try {
            Files.createDirectories(directory);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw BusinessException.badRequest("Không thể lưu file upload. Vui lòng thử lại.");
        }

        return "/uploads/" + safeFolder.replace('\\', '/') + "/" + filename;
    }

    public void deletePublicUrl(String publicUrl) {
        String clean = Objects.toString(publicUrl, "").trim();
        if (clean.isBlank() || !clean.startsWith("/uploads/")) {
            return;
        }

        String relative = clean.substring("/uploads/".length());
        int queryIndex = relative.indexOf('?');
        if (queryIndex >= 0) {
            relative = relative.substring(0, queryIndex);
        }

        Path target = uploadRoot.resolve(relative.replace('/', java.io.File.separatorChar)).normalize();
        if (!target.startsWith(uploadRoot)) {
            return;
        }

        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            throw BusinessException.badRequest("Không thể xóa file ảnh hiện tại. Vui lòng thử lại.");
        }
    }

    private static void validateReadableImage(MultipartFile file, String extension) {
        if (!Set.of("jpg", "jpeg", "png").contains(extension)) {
            return;
        }
        try (InputStream input = file.getInputStream()) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw BusinessException.badRequest("File ảnh không đọc được. Hãy chọn ảnh CCCD/CMND rõ nét.");
            }
            if (image.getWidth() < 240 || image.getHeight() < 140) {
                throw BusinessException.badRequest("Ảnh CCCD/CMND quá nhỏ. Hãy tải ảnh rõ hơn, tối thiểu 240x140px.");
            }
        } catch (IOException ex) {
            throw BusinessException.badRequest("File ảnh không đọc được. Hãy chọn ảnh JPG/PNG rõ nét.");
        }
    }

    private static String resolveExtension(String originalFilename, String contentType) {
        String name = Objects.toString(originalFilename, "");
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            return name.substring(dot + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        }
        return switch (contentType) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "";
        };
    }

    private static String safeFolder(String folder) {
        String cleaned = Objects.toString(folder, "").replace('\\', '/');
        String safe = Stream.of(cleaned.split("/+"))
                .map(LocalUploadService::safeSegment)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining("/"));
        if (safe.isBlank()) {
            throw BusinessException.badRequest("Thư mục upload không hợp lệ.");
        }
        return safe;
    }

    private static String safeSegment(String value) {
        String safe = Objects.toString(value, "")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        return safe.isBlank() ? "file" : safe;
    }
}
