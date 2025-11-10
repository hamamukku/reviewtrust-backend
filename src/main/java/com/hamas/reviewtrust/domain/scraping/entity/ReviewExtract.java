package com.hamas.reviewtrust.domain.scraping.entity;

import java.util.ArrayList;
import java.util.List;

public class ReviewExtract {
  public String reviewId;
  public String productAsin;
  public String source = "amazon";
  public Integer stars;
  public String title;
  public String text;
  public String createdAt;
  public String reviewer;
  public Boolean verified;
  public Integer helpfulCount;
  public List<String> images = new ArrayList<>();
}
