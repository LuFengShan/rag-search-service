#!/bin/bash

LOG_DIR="logs"

if [ ! -d "$LOG_DIR" ]; then
    echo "日志目录 $LOG_DIR 不存在，无需清理"
    exit 0
fi

FILE_COUNT=$(ls -1 "$LOG_DIR" 2>/dev/null | wc -l)

if [ "$FILE_COUNT" -eq 0 ]; then
    echo "日志目录 $LOG_DIR 为空，无需清理"
    exit 0
fi

echo "正在清理日志目录 $LOG_DIR 中的文件..."
rm -f "$LOG_DIR"/*

if [ $? -eq 0 ]; then
    echo "成功清理 $FILE_COUNT 个日志文件"
else
    echo "清理日志文件时发生错误"
    exit 1
fi