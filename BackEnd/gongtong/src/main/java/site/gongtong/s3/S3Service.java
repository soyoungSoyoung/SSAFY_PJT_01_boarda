package site.gongtong.s3;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.util.IOUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class S3Service implements FileService {
    private final S3Component s3Component;
    private final AmazonS3 amazonS3;

    @Override
    public String uploadFile(MultipartFile file, FileFolder fileFolder) {

        //파일 이름 생성
        String fileName = getFileFolder(fileFolder) + createFileName(file.getOriginalFilename());

        //파일 변환
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentLength(file.getSize());
        objectMetadata.setContentType(file.getContentType());

        //파일 업로드
        try (InputStream inputStream = file.getInputStream()) {
            amazonS3.putObject(new PutObjectRequest(s3Component.getBucket(), fileName, inputStream, objectMetadata));
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("파일 변환 중 에러가 발생하였습니다. (%s)", file.getOriginalFilename()));
        }

        return fileName;
    }

    @Override
    public void deleteFile(String fileName) {
        amazonS3.deleteObject(new DeleteObjectRequest(s3Component.getBucket(), fileName));
    }

    @Override
    public String getFileUrl(String fileName) {
        return amazonS3.getUrl(s3Component.getBucket(), fileName).toString();
    }

    @Override
    public byte[] downloadFile(String fileName) throws FileNotFoundException {

        //파일 유무 확인
        validateFileExists(fileName);

        S3Object s3Object = amazonS3.getObject(s3Component.getBucket(), fileName);
        S3ObjectInputStream s3ObjectContent = s3Object.getObjectContent();

        try {
            return IOUtils.toByteArray(s3ObjectContent);
        } catch (IOException e) {
            throw new FileDownloadFailedException();
        }
    }

    @Override
    public String getFileFolder(FileFolder fileFolder) {

        String folder = "";
        if (fileFolder == FileFolder.MEMBER_IMAGES) {
            folder = s3Component.getMemberFolder();

        } else if (fileFolder == FileFolder.ARTICLE_IMAGES) {
            folder = s3Component.getArticleFolder();
        } else if (fileFolder == FileFolder.REVIEW_IMAGES) {
            folder = s3Component.getReviewFolder();

        }
        return folder;
    }

    private void validateFileExists(String fileName) throws FileNotFoundException {
        if (!amazonS3.doesObjectExist(s3Component.getBucket(), fileName))
            throw new FileNotFoundException();
    }

    //파일 이름 생성 로직
    private String createFileName(String originalFileName) {
        return UUID.randomUUID().toString().concat(getFileExtension(originalFileName));
    }

    //파일의 확장자명을 가져오는 로직
    private String getFileExtension(String fileName) {
        try {
            return fileName.substring(fileName.lastIndexOf("."));
        } catch (StringIndexOutOfBoundsException e) {
            throw new IllegalArgumentException(String.format("잘못된 형식의 파일 (%s) 입니다.", fileName));
        }
    }

    //이미지 URL->파일 이름 변환
    public static String convertToFileName(String imageUrl) {
        String[] path = imageUrl.split("/");
        return path[path.length - 2] + "/" + path[path.length - 1];  //폴더 이름 + 파일 이름
    }
}
