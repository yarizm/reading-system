import sys
import os

if __name__ == "__main__":
    # Add the current directory to sys.path to allow imports from conftest.py
    current_dir = os.path.dirname(os.path.abspath(__file__))
    sys.path.insert(0, current_dir)
    
    print("==================================================")
    print("             STARTING E2E TEST RUNNER             ")
    print("==================================================")
    
    try:
        import pytest
    except ImportError:
        print("ERROR: pytest is not installed. Please install dependencies using:")
        print("pip install -r requirements.txt")
        sys.exit(1)
        
    # Run pytest on the current directory
    # -v: verbose
    # -s: don't capture stdout/stderr
    # --tb=short: short traceback representation
    args = ["-v", "-s", "--tb=short", current_dir]
    
    print(f"Executing: pytest {' '.join(args)}")
    exit_code = pytest.main(args)
    
    print("==================================================")
    print(f"E2E TESTS COMPLETED. EXIT CODE: {exit_code}")
    print("==================================================")
    
    sys.exit(exit_code)
