#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || ! "$1" =~ ^[A-Za-z0-9_][A-Za-z0-9._-]{0,127}$ ]]; then
  printf 'Usage: %s <new-immutable-image-tag>\n' "$0" >&2
  exit 2
fi

image_tag="$1"
profile="${AWS_PROFILE:-case-study-nagp}"
region="ap-southeast-2"
repository_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "$repository_root"

command -v aws >/dev/null || { printf 'AWS CLI is required.\n' >&2; exit 1; }
command -v docker >/dev/null || { printf 'Docker is required.\n' >&2; exit 1; }

account_id="$(aws sts get-caller-identity \
  --profile "$profile" \
  --region "$region" \
  --query Account \
  --output text)"
registry="${account_id}.dkr.ecr.${region}.amazonaws.com"
services=(user activity challenge integration analytics recommendation)

for service in "${services[@]}"; do
  repository="habitloop/${service}-service"
  aws ecr describe-repositories \
    --profile "$profile" \
    --region "$region" \
    --repository-names "$repository" \
    --output text >/dev/null

  if image_info="$(aws ecr describe-images \
    --profile "$profile" \
    --region "$region" \
    --repository-name "$repository" \
    --image-ids "imageTag=${image_tag}" \
    --output text 2>&1)"; then
    printf 'Image tag %s already exists in %s; choose a new immutable tag.\n' \
      "$image_tag" "$repository" >&2
    exit 1
  elif [[ "$image_info" != *ImageNotFoundException* ]]; then
    printf 'Could not verify image tag %s in %s: %s\n' \
      "$image_tag" "$repository" "$image_info" >&2
    exit 1
  fi
done

aws ecr get-login-password --profile "$profile" --region "$region" |
  docker login --username AWS --password-stdin "$registry"

for service in "${services[@]}"; do
  image="${registry}/habitloop/${service}-service:${image_tag}"
  docker build \
    --platform linux/amd64 \
    --file "backend/${service}-service/Dockerfile" \
    --tag "$image" \
    backend
done

for service in "${services[@]}"; do
  docker push "${registry}/habitloop/${service}-service:${image_tag}"
done
