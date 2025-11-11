#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Скрипт для создания иконок приложения из PNG файла
"""
from PIL import Image
import os

# Путь к исходному файлу
source_file = r"C:\Users\nml52\Desktop\KIZ_VPN.png"

# Размеры для разных плотностей
sizes = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192
}

# Размеры для foreground (адаптивные иконки)
foreground_sizes = {
    "mipmap-mdpi": 108,
    "mipmap-hdpi": 162,
    "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324,
    "mipmap-xxxhdpi": 432
}

def create_icons():
    # Открываем исходное изображение
    try:
        img = Image.open(source_file)
        print(f"Открыт файл: {source_file}")
        print(f"Размер оригинала: {img.size[0]}x{img.size[1]}")
    except Exception as e:
        print(f"Ошибка открытия файла: {e}")
        return
    
    # Обрабатываем каждую папку
    for folder, size in sizes.items():
        folder_path = f"app/src/main/res/{folder}"
        
        # Создаем папку если не существует
        os.makedirs(folder_path, exist_ok=True)
        
        # Создаем ic_launcher.png
        launcher = img.resize((size, size), Image.Resampling.LANCZOS)
        launcher_path = f"{folder_path}/ic_launcher.png"
        launcher.save(launcher_path, "PNG")
        print(f"✓ Создан: {launcher_path} ({size}x{size})")
        
        # Создаем ic_launcher_foreground.png
        fg_size = foreground_sizes[folder]
        foreground = img.resize((fg_size, fg_size), Image.Resampling.LANCZOS)
        foreground_path = f"{folder_path}/ic_launcher_foreground.png"
        foreground.save(foreground_path, "PNG")
        print(f"✓ Создан: {foreground_path} ({fg_size}x{fg_size})")
    
    print("\n✅ Все иконки успешно созданы!")
    print("\nСледующие шаги:")
    print("1. В Android Studio: File → Sync Project with Gradle Files")
    print("2. Или: Build → Rebuild Project")

if __name__ == "__main__":
    create_icons()

