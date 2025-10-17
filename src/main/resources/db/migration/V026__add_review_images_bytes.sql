-- V026__add_review_images_bytes.sql
-- add bytes column to review_images used to store binary image data
ALTER TABLE IF EXISTS review_images
  ADD COLUMN IF NOT EXISTS bytes bytea;
