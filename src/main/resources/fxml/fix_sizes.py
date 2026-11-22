import os
import glob

fxl_dir = '/Users/caeljandcristobal/Desktop/DLSU/2ND YEAR/TERM 4/LBYCPDS/AniLink/src/main/resources/fxml'

files = glob.glob(os.path.join(fxl_dir, '*.fxml'))

for fxml_file in sorted(files):
    with open(fxml_file, 'r') as f:
        content = f.read()
    
    # Replace all prefHeight values with 750.0
    old_content = content
    content = content.replace('prefHeight="600.0"', 'prefHeight="750.0"')
    content = content.replace('prefHeight="650.0"', 'prefHeight="750.0"')
    content = content.replace('prefHeight="700.0"', 'prefHeight="750.0"')
    
    # Also ensure prefWidth is consistent at 1100.0
    content = content.replace('prefWidth="900.0"', 'prefWidth="1100.0"')
    content = content.replace('prefWidth="1000.0"', 'prefWidth="1100.0"')
    
    if content != old_content:
        with open(fxml_file, 'w') as f:
            f.write(content)
        print(f'UPDATED: {os.path.basename(fxml_file)}')
    else:
        print(f'UNCHANGED: {os.path.basename(fxml_file)}')