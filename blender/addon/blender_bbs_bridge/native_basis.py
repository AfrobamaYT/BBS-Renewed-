"""Align an authored native rig to the model-space frames actually stored in BOBJ."""
import math
from mathutils import Matrix

KEY = 'bbs_native_basis_v1'
WORLD_AXES = Matrix(((1, 0, 0, 0), (0, 0, -1, 0),
                     (0, 1, 0, 0), (0, 0, 0, 1)))


class BasisError(RuntimeError):
    pass


def target_frames(path):
    frames = {}
    with open(path, encoding='utf-8') as source:
        for line in source:
            if not line.startswith('arm_bone '):
                continue
            fields = line.split()
            if len(fields) < 21:
                raise BasisError('Invalid BOBJ rest frame')
            values = [float(value) for value in fields[-16:]]
            if not all(math.isfinite(value) for value in values):
                raise BasisError('Non-finite BOBJ rest frame')
            frames[fields[1]] = WORLD_AXES @ Matrix(
                [values[start:start+4] for start in range(0, 16, 4)])
    return frames


def difference(a, b):
    return max(abs(a[row][col] - b[row][col]) for row in range(4) for col in range(4))


def alignment(root, path):
    pairs = [(root.data.bones[name].matrix_local.copy(), target)
             for name, target in target_frames(path).items() if name in root.data.bones]
    if not pairs:
        raise BasisError('Native model and BOBJ have no common bone frames')
    basis = pairs[0][1] @ pairs[0][0].inverted()
    if max(difference(basis @ source, target) for source, target in pairs) > 1e-4:
        raise BasisError('Native model rest frames disagree with BOBJ; no single alignment exists')
    rotation = basis.to_3x3()
    orthogonal = (rotation.transposed() @ rotation).to_4x4()
    if difference(orthogonal, Matrix.Identity(4)) > 1e-4 or abs(rotation.determinant()-1) > 1e-4:
        raise BasisError('Native model alignment includes unsupported scale or reflection')
    return basis, len(pairs)


def apply(root, path):
    basis, matched = alignment(root, path)
    if difference(basis, Matrix.Identity(4)) < 1e-5:
        return {'matched': matched, 'changed': False}
    if root.mode == 'EDIT':
        raise BasisError('Native model alignment requires leaving Edit Mode')
    children = list(root.children)
    if any(child.parent_type not in ('OBJECT', 'BONE') for child in children):
        raise BasisError('Unsupported native asset parenting')
    # Rest frames and the mesh-to-armature bind space must change together. The
    # parent inverse preserves authored child channels, mesh/shape-key data and
    # the actor's own BBS transform. Bone-parented objects already follow the bone.
    if root.data.users > 1:
        root.data = root.data.copy()
    root.data.transform(basis)
    for child in children:
        if child.parent_type == 'OBJECT':
            child.matrix_parent_inverse = basis @ child.matrix_parent_inverse
    root[KEY] = [value for row in basis for value in row]
    root.update_tag()
    return {'matched': matched, 'changed': True, 'basis': [list(row) for row in basis]}
