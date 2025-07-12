import subprocess
import sys

def install(package):
    """Internal helper to install a single package using pip"""
    subprocess.check_call([sys.executable, "-m", "pip", "install", package])

def install_required_packages(required_packages):
    for package in required_packages:
        try:
            __import__(package.split('==')[0])  # 忽略版本号检查
        except ImportError:
            install(package)
