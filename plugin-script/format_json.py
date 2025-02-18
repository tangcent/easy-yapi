import os
import json
from pathlib import Path

def is_json(content):
    try:
        json.loads(content)
        return True
    except ValueError:
        return False

def format_json_file(file_path):
    try:
        # Read the file content
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Check if content is valid JSON
        if is_json(content):
            # Parse and format JSON
            parsed = json.loads(content)
            formatted = json.dumps(parsed, indent=2, ensure_ascii=False)
            
            # Write back formatted JSON
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(formatted)
            print(f"✓ Formatted JSON file: {file_path}")
            return True
        return False
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
    formatted_count = 0
    total_files = 0
    
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
                    total_files += 1
                    if format_json_file(file_path):
                        formatted_count += 1
    
    print(f"\nSummary:")
    print(f"Total files scanned: {total_files}")
    print(f"JSON files formatted: {formatted_count}")

if __name__ == "__main__":
    main() 