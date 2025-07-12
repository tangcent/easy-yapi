import os
import json
import re
import sys
from datetime import datetime
from pathlib import Path
from install_requirements import install_required_packages

# Note: pytz dependency removed as it's not actually needed for the time processing logic

def is_json(content):
    try:
        json.loads(content)
        return True
    except ValueError:
        return False

def build_time_strings():
    """Build LOCAL_TIME and STANDARD_TIME strings from fixed timestamp"""
    timestamp = 1618124194123 / 1000  # Convert to seconds
    utc_time = datetime.utcfromtimestamp(timestamp)
    local_time = datetime.fromtimestamp(timestamp)
    
    # Standard time formats (UTC)
    stand_times = {
        'gmt': utc_time.strftime("%a, %d %b %Y%H:%M:%S GMT"),
        'iso': utc_time.strftime("%Y-%m-%d %H:%M:%S"),
        'compact': utc_time.strftime("%Y%m%d%H%M%S")
    }
    
    # Local time formats
    local_times = {
        'gmt': local_time.strftime("%a, %d %b %Y%H:%M:%S GMT"),
        'iso': local_time.strftime("%Y-%m-%d %H:%M:%S"), 
        'compact': local_time.strftime("%Y%m%d%H%M%S")
    }
    
    return local_times, stand_times

def replace_local_to_standard_time(content: str) -> str:
    """Replace all LOCAL_TIME strings with STANDARD_TIME strings"""
    local_times, stand_times = build_time_strings()
    
    # Replace each format
    content = content.replace(local_times['gmt'], stand_times['gmt'])
    content = content.replace(local_times['iso'], stand_times['iso'])
    content = content.replace(local_times['compact'], stand_times['compact'])
    
    return content

# 用字典存储计数器，避免global
counts = {
    'formatted_count': 0,
    'json_files_count': 0,
    'total_files': 0
}

def process_test_resources(file_path):
    try:
        # Read the file content
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
        # First replace local times with standard times
        content = replace_local_to_standard_time(content)
        # Then check if content is valid JSON
        if is_json(content):
            # Parse and format JSON
            parsed = json.loads(content)
            formatted = json.dumps(parsed, indent=2, ensure_ascii=False)
            # Write back processed content
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(formatted)
            print(f"✓ Processed file: {file_path}")
            counts['formatted_count'] += 1
            counts['json_files_count'] += 1
            return True
        # For non-JSON files, just write back with time replacements
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"✓ Processed file (time only): {file_path}")
        counts['formatted_count'] += 1
        return True
    except Exception as e:
        # Skip binary files and files that can't be read as text
        if "codec can't decode byte" not in str(e):
            print(f"✗ Error processing {file_path}: {str(e)}")
        return False

def find_project_root():
    """Find the project root directory by looking for .git folder"""
    # Get the directory containing this script
    script_dir = Path(__file__).resolve().parent
    
    # Go up one level to get to project root
    project_root = script_dir.parent
    
    # Verify this is actually the project root by checking for .git
    if not (project_root / '.git').exists():
        raise RuntimeError("Could not find project root (no .git folder found)")
        
    return project_root

def main():
    print("Scanning for JSON files in test/resources/result folders...")
    
    # Get the project root directory
    project_root = find_project_root()
    print(f"Using project root: {project_root}")
    
    # Find all test/resources/result folders
    result_dirs = []
    for path in project_root.rglob('**/test/resources/result'):
        if path.is_dir():
            result_dirs.append(path)
    
    if not result_dirs:
        print("No test/resources/result folders found!")
        return
        
    print(f"Found {len(result_dirs)} result folder(s)")
    
    # Process each result directory
    for result_dir in result_dirs:
        print(f"\nProcessing folder: {result_dir}")
        # Process all files in the result directory
        for file_path in result_dir.glob('*'):
            if file_path.is_file():
                # Only process files that might be JSON
                if file_path.suffix.lower() in ['.json', '.txt', ''] or 'json' in file_path.name.lower():
                    counts['total_files'] += 1
                    process_test_resources(file_path)
    
    print(f"\nSummary:")
    print(f"Total files scanned: {counts['total_files']}")
    print(f"Files processed: {counts['formatted_count']}")
    print(f"JSON files formatted: {counts['json_files_count']}")

if __name__ == "__main__":
    main()
