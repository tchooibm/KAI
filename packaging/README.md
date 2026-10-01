# Kai

Kai reads your team's documents and tells you which ones need updating when something
changes. You type the change in plain words, for example *"We now require Java 21 instead
of 17"*. Kai lists every document that mentions it, says why, and proposes the edit.

Kai reads Word (.docx), PowerPoint (.pptx), PDF and text files. It never changes a file
without your approval, and it backs up every file before it changes it.

You do not need to install anything. Kai brings everything it needs with it.

## What is in the folder

| File | What it is |
|---|---|
| `kai.exe` (Windows) or `kai` (Mac) | The Kai program |
| `kai.properties` | Kai's settings: which folders to check, where to keep backups, and the AI connection |
| `README.md` | This guide |

Keep these files together in **one folder**. Kai looks for `kai.properties` next to itself.

When Kai starts for the first time, it adds a folder called `.kai-runtime` (100 MB or more) with
the parts it needs to run. It is hidden on a Mac. Leave it alone. Kai manages it by itself.

Put the Kai folder somewhere on your own computer, for example in `Documents`. Do not put it in
a Box Drive, OneDrive or other synced folder, or `.kai-runtime` gets uploaded too.

## 1. Unzip Kai

- **Windows:** right-click `kai-windows.zip` → **Extract All…**, and pick a folder, for
  example `Documents\Kai`. Do not start Kai from inside the zip. It cannot find its settings there.
- **Mac:** double-click `kai-macos.zip`, then move the `macos` folder where you like, for
  example to `Documents` (you can rename it `Kai`).

## 2. Set up kai.properties

Open `kai.properties` in a plain text editor: **Notepad** on Windows (right-click → **Open with**
→ **Notepad**), **TextEdit** on Mac (right-click → **Open With** → **TextEdit**). Do not use Word.

A few rules for the whole file:

- A line that starts with `#` is a note. Kai ignores it. To switch a setting on, remove the `#`.
- Each setting is one line: `name=value`. Do not change the part before `=`.
- **Relative paths** such as `./docs` start from the folder where `kai.properties` is.
  `~/` means your home folder, for example `~/Documents/Runbooks`.
- **Windows paths** can be pasted as they are, for example from right-click → **Copy as path**.
  `C:\Team\Docs` and `"C:\Team\Docs"` both work.
- **Mac paths:** right-click the folder in Finder, hold **Option**, and choose **Copy "…" as Pathname**.

Below is every setting, in the order you will find it in the file.

### Folders to check (required)

```
kai.scan.local=./docs
```

The folder or folders with your documents. Kai also checks every folder inside them. To check
several folders, separate them with commas:

```
kai.scan.local=~/Documents/Runbooks, C:\Team\Docs
```

**Box Drive** and other synced folders (OneDrive, shared drives) work like any other folder.
Example on a Mac: `kai.scan.local=~/Library/CloudStorage/Box-Box/Team Docs`.
Files that are only in the cloud and not downloaded to your computer are skipped.

Leave the `# kai.scan.box=…` line as it is (with the `#`). Box web links are not supported yet.

### Backup folder (required)

```
kai.backup-dir=./kai-backups
```

Before Kai changes any file, it copies the original here, together with a report of every
change. Kai creates the folder if it is not there yet. It must **not** be inside one of the
folders you check, for example `kai.backup-dir=~/kai-backups`.

### Speed

```
kai.scan.parallel=4
```

How many files Kai checks at the same time. Leave it at `4`. If many files show
**Could not check** with "429" or "rate limit", lower it to `2` or `1`.

### AI connection (ask your Kai admin)

```
spring.ai.openai.api-key=${ICA_CODEX_KEY}
spring.ai.openai.base-url=${ICA_BASE_URL_V1}
spring.ai.openai.chat.model=gpt-5.6-terra
```

Your Kai admin tells you what to put here.

- `api-key` is your key for the AI service. Keep it private, like a password, and do not share
  your `kai.properties` with others once your key is in it.
- `base-url` is the address of the AI service. It must end with `/v1`.
- `chat.model` is the AI model's name.
- `${...}` means "take the value from this computer's settings". If your admin set that up for
  you, leave those lines as they are. Otherwise replace the whole `${...}` with the value, for
  example `spring.ai.openai.api-key=sk-abc123`.

### Web address (only if needed)

```
# server.port=8080
```

Kai opens a page at **http://localhost:8080**. Only if Kai says that it cannot start because
the port is in use: remove the `#` and change `8080` to another number, such as `8090`.
The address then becomes http://localhost:8090.

**Save the file** when you are done (keep the name exactly `kai.properties`).

## 3. Start Kai

### Windows

Double-click `kai.exe`. A black window opens.

The first time, Windows may show **"Windows protected your PC"**. Click **More info** →
**Run anyway**. This happens because Kai is an in-house program, not one from a store.

### Mac

Double-click `kai`. A Terminal window opens.

The first time, the Mac may say **"kai" cannot be opened** or **"Apple could not verify…"**.
Click **Done** (or **OK**), then open **System Settings** → **Privacy & Security**, scroll down,
and click **Open Anyway** next to the message about `kai`. On older Macs: right-click `kai` →
**Open** → **Open**. You only have to do this once.

### Both

The **first start takes longer** (up to a minute) because Kai unpacks itself into a folder
called `.kai-runtime` next to the program. Later starts are faster. Kai then checks your settings and the AI connection, and shows:

```
Kai is running at http://localhost:8080
Keep this window open while you use Kai. Close it to stop Kai.
```

Open **http://localhost:8080** in your browser (Chrome, Edge, Safari, …).

**Keep the window open** while you use Kai. To stop Kai, close the window (or press
**Ctrl+C** in it). On a Mac the window then says *[Process completed]*, and you can close it.

## 4. Use Kai

Type the change in the box at the bottom and click **Scan documents**. You can write one
sentence or paste release notes several paragraphs long. **Enter** starts a new line. Only
the button sends.

While Kai works, a live log shows what it is doing for each file. The results appear by
themselves when the scan is done. You can refresh the page in the meantime. One scan runs at a time.

The results are grouped by **Location**: each folder from `kai.scan.local`, with how many of
its files are affected. Each file gets one of these results:

| Result | Meaning |
|---|---|
| **Must change** | The file mentions the change. Kai shows its proposed edit below it |
| **Could not apply** | The file must change, but Kai could not place its edit in the text. Update it yourself |
| **Update by hand** | The file mentions the change, but it is a Word, PowerPoint or PDF file. Open it and change it yourself |
| **Could not check** | Kai could not read the file or reach the AI. Check this file yourself, or try again |
| **No change needed** | Click to see the list. These files do not need updating |

Under each **Must change** file you see:

- **What changes:** removed lines in red (−), new lines in green (+). Unchanged parts are folded.
- **The reviewer's note:** "looks right", or what to check.
- **Edit:** opens the proposed text so you can change it. Click **Save edits** and the red and
  green view updates.
- **Include this file:** untick it to leave that file as it is.

Saving edits does **not** change your files yet.

When you are happy, click **Finalize** and confirm. Kai then:

1. Checks that no file changed since the scan. If one did, Kai writes nothing and asks you to
   scan again, so it never overwrites someone else's newer work.
2. Saves a report of every change (before and after) and backs up the original files, both in
   your backup folder.
3. Changes the ticked files. If changing any file fails, Kai puts **all** of them back from the
   backup and says **Rolled back**, so your files are exactly as before.

The reply has an **Open the report** link.

## If something goes wrong

Kai checks its settings and the AI connection when it starts. If something is wrong, the
window says what to fix and waits for you to press **Enter**. Fix `kai.properties`, save it,
and start Kai again.

| Message | What to do |
|---|---|
| *Kai could not find its settings file* | Put `kai.properties` in the same folder as `kai.exe` / `kai`. Check the name: Windows sometimes saves it as `kai.properties.txt`. In File Explorer turn on **View** → **Show** → **File name extensions** to see the full name |
| *Folder to scan not found* | Check the path after `kai.scan.local`. Copy it again from File Explorer or Finder |
| *kai.backup-dir is missing* | Add the line `kai.backup-dir=./kai-backups` |
| *The backup folder … is inside a folder Kai scans* | Choose a backup folder outside your document folders, for example `kai.backup-dir=~/kai-backups` |
| *Nothing to scan* | Add a `kai.scan.local=...` line with at least one folder |
| *Box folders are not supported yet* | Put a `#` in front of the `kai.scan.box` line |
| *Unknown setting* | A setting name is misspelled. Compare it with the list in the message |
| *kai.scan.parallel must be a whole number* | Use a number such as `4`, without spaces or letters |
| *The AI key is not set* / *The AI address is not set* | Fill in the AI connection section with the values from your Kai admin |
| *The AI address must end with /v1* | Add `/v1` to the end of `spring.ai.openai.base-url` |
| *The AI service refused the key* | The key is wrong or has expired. Ask your Kai admin for a new one |
| *The AI service does not know this address or model* | Check the address and the model name with your Kai admin |
| *Kai cannot reach the AI service* | Check that you are online, and on the company VPN if you need one |
| *Kai could not start: unpacking Java failed* | Kai needs to write into its own folder. Move the Kai folder somewhere you can save files, such as `Documents` (not `Program Files`, and not a read-only shared drive). Also check that your disk is not full |
| *Kai could not start. The reason is shown above.* | Often the port is in use ("Port 8080 was already in use"). Kai may already be running in another window. Use that one, or close it. Otherwise change `server.port` (see above) |
| Many files show **Could not check** | If you see "429" or "rate limit", set `kai.scan.parallel=1`. Otherwise start Kai again: it checks the AI connection when it starts |

## Updating Kai

When you get a new version, replace only `kai.exe` / `kai`. Keep your own `kai.properties`
and your backup folder.

Close Kai before you replace it. The new version replaces its `.kai-runtime` folder by itself
the first time it starts.

To remove Kai completely, delete the Kai folder. That removes everything, including
`.kai-runtime`. Keep your backup folder if it is somewhere else and you still need it.
