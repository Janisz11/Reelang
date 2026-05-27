import boto3
import os
import logging

logger = logging.getLogger(__name__)


def get_r2_client():
    return boto3.client(
        "s3",
        endpoint_url=f"https://{os.getenv('R2_ACCOUNT_ID')}.r2.cloudflarestorage.com",
        aws_access_key_id=os.getenv("R2_ACCESS_KEY_ID"),
        aws_secret_access_key=os.getenv("R2_SECRET_ACCESS_KEY"),
        region_name="auto"
    )


def upload_to_r2(local_path: str, key: str, content_type: str = "video/mp4") -> bool:
    try:
        client = get_r2_client()
        client.upload_file(
            local_path,
            os.getenv("R2_BUCKET_NAME", "reelang-uploads"),
            key,
            ExtraArgs={"ContentType": content_type}
        )
        logger.info(f"Uploaded {key} to R2")
        return True
    except Exception as e:
        logger.error(f"Failed to upload to R2: {e}")
        return False


def get_presigned_url(key: str, expires: int = 3600) -> str | None:
    try:
        client = get_r2_client()
        url = client.generate_presigned_url(
            "get_object",
            Params={
                "Bucket": os.getenv("R2_BUCKET_NAME", "reelang-uploads"),
                "Key": key
            },
            ExpiresIn=expires
        )
        return url
    except Exception as e:
        logger.error(f"Failed to generate presigned URL: {e}")
        return None


def delete_from_r2(key: str) -> bool:
    try:
        client = get_r2_client()
        client.delete_object(
            Bucket=os.getenv("R2_BUCKET_NAME", "reelang-uploads"),
            Key=key
        )
        return True
    except Exception as e:
        logger.error(f"Failed to delete from R2: {e}")
        return False
