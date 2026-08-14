#!/bin/sh
set -eu

if [ "$#" -eq 0 ]; then
  echo "usage: ./scripts/check.sh <focused|full> [changed-path ...]" >&2
  exit 2
fi

mode=$1
shift

case "$mode" in
  focused|full) ;;
  *)
    echo "usage: ./scripts/check.sh <focused|full> [changed-path ...]" >&2
    exit 2
    ;;
esac

exec java scripts/governance/GovernanceCheck.java "$mode" "$@"
