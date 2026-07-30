package com.sprintlog.sprintlogboot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
@ConditionalOnProperty(name = "sprintlog.storage", havingValue = "S3")
public class S3Service implements FileStorage{

  @Override
  public String saveFile(MultipartFile file) {
    return "";
  }

  @Override
  public String getFileUrl(String storedName) {
    return "";
  }

  @Override
  public String getDownloadUrl(String storedName) {
    return "";
  }

  @Override
  public void deleteFile(String storedName) {

  }
}
