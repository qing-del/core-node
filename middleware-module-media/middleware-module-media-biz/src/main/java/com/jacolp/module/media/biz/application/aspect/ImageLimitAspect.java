package com.jacolp.module.media.biz.application.aspect;

import com.jacolp.constant.ImageConstant;
import com.jacolp.exception.BaseException;
import com.jacolp.module.media.biz.application.annotation.ImageLimit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Aspect
@Component
@Order(1)
public class ImageLimitAspect {
    @Pointcut("@annotation(imageLimit)")
    public void imageLimitPointcut(ImageLimit imageLimit) {
    }

    @Around("imageLimitPointcut(imageLimit)")
    public Object checkImageLimit(ProceedingJoinPoint joinPoint, ImageLimit imageLimit) throws Throwable {
        MultipartFile file = null;
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof MultipartFile multipartFile) {
                file = multipartFile;
                break;
            }
        }
        if (file == null || file.isEmpty()) {
            throw new BaseException(ImageConstant.IMAGE_FILE_EMPTY);
        }
        if (file.getSize() > imageLimit.maxBytes()) {
            throw new BaseException(ImageConstant.IMAGE_FILE_TOO_LARGE);
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new BaseException(ImageConstant.IMAGE_INVALID_FORMAT);
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        for (String format : ImageConstant.ALLOWED_IMAGE_FORMATS) {
            if (format.equals(extension)) {
                return joinPoint.proceed();
            }
        }
        throw new BaseException(ImageConstant.IMAGE_INVALID_FORMAT);
    }
}
