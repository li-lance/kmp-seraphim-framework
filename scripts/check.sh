#!/bin/sh
set -eu

if [ "$#" -eq 0 ]; then
  echo "usage: ./scripts/check.sh <focused|full> [changed-path ...]" >&2
  exit 2
fi

mode=$1
shift

repository_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

case "$mode" in
  focused|full) ;;
  *)
    echo "usage: ./scripts/check.sh <focused|full> [changed-path ...]" >&2
    exit 2
    ;;
esac

cd "$repository_root"
exec java scripts/governance/GovernanceCheck.java "$mode" "$@"
