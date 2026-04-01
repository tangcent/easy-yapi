package org.springframework.web.multipart;

public interface MultipartFile {
    String getName();
    String getOriginalFilename();
    String getContentType();
    boolean isEmpty();
    long getSize();
    byte[] getBytes();
    java.io.InputStream getInputStream() throws java.io.IOException;
    void transferTo(java.io.File dest) throws java.io.IOException, IllegalStateException;
}
