# Start the external viewer

The Minecraft plugin writes live simulation state to:

```text
plugins/CivMicroscope/state/
```

Install viewer dependencies:

```powershell
python -m pip install -r .\viewer\requirements.txt
```

Start it:

```powershell
python .\viewer\viewer.py --state "C:\path\to\paper\plugins\CivMicroscope\state"
```

It is independent of the Minecraft client and can run at the same time as the
Paper server.
