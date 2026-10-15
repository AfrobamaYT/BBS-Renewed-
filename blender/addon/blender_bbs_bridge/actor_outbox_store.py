"""SQLite-backed intent/receipt journal. No bpy and no network calls.

An intent (including its client sequence) commits before send. A result commits
before it advances Blender's local baseline. Records remain available when an
older .blend is loaded; neither timeout nor reconnect deletes user intent.
"""
import copy
import hashlib
import json
import pathlib
import sqlite3


def encode(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), allow_nan=False)


class Outbox:
    MAX_RECORDS = 1024
    MAX_RECORD_BYTES = 16 * 1024 * 1024

    def __init__(self, path):
        self.path = pathlib.Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.db = sqlite3.connect(self.path, timeout=0.1, isolation_level=None)
        self.db.execute('PRAGMA synchronous=FULL')
        application = self.db.execute('PRAGMA application_id').fetchone()[0]
        version = self.db.execute('PRAGMA user_version').fetchone()[0]
        tables = self.db.execute("SELECT name FROM sqlite_schema WHERE type='table'").fetchall()
        if (version == 0 and tables) or (version in (1, 2, 3) and application != 1111642931):
            self.close()
            raise RuntimeError('Not a BBS actor outbox database; left unchanged')
        if version not in (0, 1, 2, 3):
            self.close()
            raise RuntimeError('Unsupported actor outbox schema')
        if version == 0:
            self.db.executescript('''BEGIN IMMEDIATE;
                CREATE TABLE IF NOT EXISTS documents(
                    document TEXT PRIMARY KEY, sequence INTEGER NOT NULL,
                    baseline TEXT, baseline_digest TEXT, baseline_sequence INTEGER NOT NULL DEFAULT 0);
                CREATE TABLE IF NOT EXISTS operations(
                    document TEXT NOT NULL, sequence INTEGER NOT NULL, request_id TEXT NOT NULL UNIQUE,
                    intent TEXT NOT NULL, digest TEXT NOT NULL, sent INTEGER NOT NULL DEFAULT 0,
                    receipt TEXT, PRIMARY KEY(document, sequence));
                PRAGMA application_id=1111642931;
                PRAGMA user_version=2;
                COMMIT;''')
        elif version == 1:
            self.db.executescript('''BEGIN IMMEDIATE;
                ALTER TABLE documents ADD COLUMN baseline TEXT;
                ALTER TABLE documents ADD COLUMN baseline_digest TEXT;
                ALTER TABLE documents ADD COLUMN baseline_sequence INTEGER NOT NULL DEFAULT 0;
                PRAGMA user_version=2;
                COMMIT;''')
        if version < 3:
            self.db.executescript('''BEGIN IMMEDIATE;
                CREATE TABLE observations(
                    id INTEGER PRIMARY KEY AUTOINCREMENT, document TEXT NOT NULL,
                    after_sequence INTEGER NOT NULL, scope TEXT NOT NULL,
                    checkpoint TEXT NOT NULL, digest TEXT NOT NULL);
                CREATE INDEX observations_document ON observations(document,id);
                PRAGMA user_version=3;
                COMMIT;''')
        if self.db.execute('PRAGMA quick_check').fetchone()[0] != 'ok':
            self.close()
            raise RuntimeError('Actor outbox integrity check failed; file retained')

    def close(self):
        self.db.close()

    def initialize(self, document):
        self.db.execute(
            'INSERT OR IGNORE INTO documents(document,sequence,baseline_sequence) VALUES(?,0,0)',
            (document,))

    def baseline(self, document):
        row = self.db.execute(
            'SELECT baseline,baseline_digest,baseline_sequence FROM documents WHERE document=?',
            (document,)).fetchone()
        if row is None:
            raise RuntimeError('Missing durable document row')
        text, digest, sequence = row
        if text is None:
            return None, int(sequence)
        if hashlib.sha256(text.encode()).hexdigest() != digest:
            raise RuntimeError('Shared history baseline checksum mismatch; no undo sent')
        return json.loads(text), int(sequence)

    def has_baseline(self, document):
        row = self.db.execute('SELECT baseline IS NOT NULL FROM documents WHERE document=?',
                              (document,)).fetchone()
        return row is not None and bool(row[0])

    def set_baseline(self, document, sequence, baseline):
        text = encode(baseline)
        if len(text.encode()) > self.MAX_RECORD_BYTES:
            raise RuntimeError('Shared history baseline exceeds the durable size limit')
        digest = hashlib.sha256(text.encode()).hexdigest()
        self.db.execute('BEGIN IMMEDIATE')
        try:
            row = self.db.execute('SELECT sequence FROM documents WHERE document=?', (document,)).fetchone()
            if row is None or sequence < 0 or sequence > row[0]:
                raise RuntimeError('Invalid shared history baseline sequence')
            self.db.execute(
                'UPDATE documents SET baseline=?,baseline_digest=?,baseline_sequence=? WHERE document=?',
                (text, digest, int(sequence), document))
            self.db.execute('DELETE FROM observations WHERE document=? AND after_sequence<=?',
                            (document, int(sequence)))
            self.db.execute('COMMIT')
        except BaseException:
            self.db.execute('ROLLBACK')
            raise

    def observe(self, document, sequence, scope, checkpoint):
        """Record accepted remote effects after the latest locally acknowledged operation."""
        text = encode({'sequence': sequence, 'scope': scope, 'checkpoint': checkpoint})
        if len(text.encode()) > self.MAX_RECORD_BYTES:
            raise RuntimeError('Remote history checkpoint exceeds the durable size limit')
        digest = hashlib.sha256(text.encode()).hexdigest()
        self.db.execute('BEGIN IMMEDIATE')
        try:
            row = self.db.execute('SELECT sequence FROM documents WHERE document=?', (document,)).fetchone()
            if row is None or sequence < 0 or sequence > row[0]:
                raise RuntimeError('Invalid remote observation sequence')
            last = self.db.execute(
                'SELECT id,after_sequence,scope FROM observations WHERE document=? ORDER BY id DESC LIMIT 1',
                (document,)).fetchone()
            # Repeated drag updates between two local operations need only their last state.
            if last is not None and last[1:] == (sequence, scope):
                self.db.execute('DELETE FROM observations WHERE id=?', (last[0],))
            else:
                count = self.db.execute('SELECT COUNT(*) FROM observations WHERE document=?',
                                        (document,)).fetchone()[0]
                if count >= self.MAX_RECORDS * 4:
                    raise RuntimeError('Remote history capacity reached; resync required')
            cursor = self.db.execute(
                'INSERT INTO observations(document,after_sequence,scope,checkpoint,digest) VALUES(?,?,?,?,?)',
                (document, int(sequence), scope, text, digest))
            observation_id = int(cursor.lastrowid)
            self.db.execute('COMMIT')
            return observation_id
        except BaseException:
            self.db.execute('ROLLBACK')
            raise

    def observations(self, document, after=0):
        result = []
        for row_id, sequence, scope, text, digest in self.db.execute(
                'SELECT id,after_sequence,scope,checkpoint,digest FROM observations '
                'WHERE document=? AND after_sequence>=? ORDER BY after_sequence,id', (document, after)):
            if hashlib.sha256(text.encode()).hexdigest() != digest:
                raise RuntimeError('Remote history checkpoint checksum mismatch; no undo sent')
            saved = json.loads(text)
            if saved.get('sequence') != sequence or saved.get('scope') != scope:
                raise RuntimeError('Remote history ordering metadata mismatch')
            checkpoint = saved['checkpoint']
            checkpoint['_observation_id'] = int(row_id)
            result.append((int(sequence), checkpoint))
        return result

    def enqueue(self, document, record):
        """Atomically allocate an identity and persist an immutable command."""
        record = copy.deepcopy(record)
        self.db.execute('BEGIN IMMEDIATE')
        try:
            count = self.db.execute('SELECT COUNT(*) FROM operations WHERE document=?', (document,)).fetchone()[0]
            if count >= self.MAX_RECORDS:
                raise RuntimeError('Actor outbox history capacity reached; nothing discarded')
            self.initialize(document)
            sequence = self.db.execute('SELECT sequence FROM documents WHERE document=?', (document,)).fetchone()[0] + 1
            record['payload'].update(client=document, transactionSeq=sequence)
            text = encode(record)
            if len(text.encode()) > self.MAX_RECORD_BYTES:
                raise RuntimeError('Actor operation exceeds outbox size limit')
            self.db.execute('INSERT INTO operations(document,sequence,request_id,intent,digest) VALUES(?,?,?,?,?)',
                            (document, sequence, record['payload']['requestId'], text,
                             hashlib.sha256(text.encode()).hexdigest()))
            self.db.execute('UPDATE documents SET sequence=? WHERE document=?', (sequence, document))
            self.db.execute('COMMIT')
            return record
        except BaseException:
            self.db.execute('ROLLBACK')
            raise

    def records(self, document, after=0):
        records = []
        for sequence, text, digest, sent, receipt in self.db.execute(
                'SELECT sequence,intent,digest,sent,receipt FROM operations WHERE document=? AND sequence>? ORDER BY sequence',
                (document, after)):
            if hashlib.sha256(text.encode()).hexdigest() != digest:
                raise RuntimeError('Actor outbox intent checksum mismatch; no command sent')
            record = json.loads(text)
            if record['payload']['transactionSeq'] != sequence or record['payload']['client'] != document:
                raise RuntimeError('Actor outbox identity mismatch')
            record.update(sent=bool(sent), receipt=json.loads(receipt) if receipt is not None else None)
            records.append(record)
        return records

    def mark_sent(self, document, sequence, sent=True):
        cursor = self.db.execute('UPDATE operations SET sent=? WHERE document=? AND sequence=? AND receipt IS NULL',
                                 (int(sent), document, sequence))
        if cursor.rowcount != 1:
            raise RuntimeError('Missing or already confirmed actor intent')

    def confirm(self, document, sequence, receipt):
        text = encode(receipt)
        self.db.execute('BEGIN IMMEDIATE')
        try:
            row = self.db.execute('SELECT request_id,receipt FROM operations WHERE document=? AND sequence=?',
                                  (document, sequence)).fetchone()
            if row is None or receipt.get('requestId') != row[0]:
                raise RuntimeError('Actor receipt does not match the journaled intent')
            if row[1] is not None and json.loads(row[1]) != receipt:
                raise RuntimeError('Contradictory actor receipt; original retained')
            self.db.execute('UPDATE operations SET receipt=? WHERE document=? AND sequence=? AND receipt IS NULL',
                            (text, document, sequence))
            self.db.execute('COMMIT')
        except BaseException:
            self.db.execute('ROLLBACK')
            raise
