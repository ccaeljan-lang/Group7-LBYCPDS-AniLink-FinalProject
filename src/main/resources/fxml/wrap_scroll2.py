import os
import re
import glob

fxl_dir = '/Users/caeljandcristobal/Desktop/DLSU/2ND YEAR/TERM 4/LBYCPDS/AniLink/src/main/resources/fxml'

files = ['Login.fxml', 'Register.fxml']

for fxml_file in files:
    filepath = os.path.join(fxl_dir, fxml_file)
    with open(filepath, 'r') as f:
        content = f.read()
    
    # Skip if already wrapped in ScrollPane
    if '<ScrollPane' in content:
        print(f'SKIP (already has ScrollPane): {fxml_file}')
        continue
    
    # Find the right-side VBox (the one with form fields, not the image VBox)
    # For Login.fxml: second VBox is the form
    # For Register.fxml: second VBox is the form
    
    # Find all VBox tags that have alignment="CENTER" (the form panels)
    # We need to wrap the right-side VBox in ScrollPane
    
    # Find the second <VBox> which is the form container
    first_vbox_end = content.find('</VBox>') + len('</VBox>')
    second_vbox_start = content.find('<VBox', first_vbox_end)
    
    if second_vbox_start == -1:
        print(f'SKIP (no second VBox): {fxml_file}')
        continue
    
    # Find the closing tag of the second VBox
    second_vbox_end = content.find('</VBox>', second_vbox_start)
    if second_vbox_end == -1:
        print(f'SKIP (no closing VBox): {fxml_file}')
        continue
    second_vbox_end += len('</VBox>')
    
    # Extract the content of the second VBox
    second_vbox_content = content[second_vbox_start:second_vbox_end]
    
    # Wrap in ScrollPane
    wrapped = f'''<ScrollPane fitToWidth="true" hbarPolicy="NEVER" vbarPolicy="AS_NEEDED" VBox.vgrow="ALWAYS" style="-fx-background-color: transparent;">
{second_vbox_content}
</ScrollPane>'''
    
    new_content = content[:second_vbox_start] + wrapped + content[second_vbox_end:]
    
    with open(filepath, 'w') as f:
        f.write(new_content)
    
    print(f'UPDATED: {fxml_file}')