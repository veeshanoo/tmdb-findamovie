package dev.skyit.tmdb_findyourmovie.ui.profile

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import by.kirich1409.viewbindingdelegate.viewBinding
import coil.Coil
import coil.load
import coil.request.ImageRequest
import com.github.dhaval2404.imagepicker.ImagePicker
import dagger.hilt.android.AndroidEntryPoint
import dev.skyit.tmdb_findyourmovie.R
import dev.skyit.tmdb_findyourmovie.databinding.FragmentProfileBinding
import dev.skyit.tmdb_findyourmovie.databinding.ListItemRecentlyWatchedBinding
import dev.skyit.tmdb_findyourmovie.db.Models.MovieDb
import dev.skyit.tmdb_findyourmovie.generic.BaseFragment
import dev.skyit.tmdb_findyourmovie.ui.utils.SimpleRecyclerAdapter
import dev.skyit.tmdb_findyourmovie.ui.utils.errAlert
import dev.skyit.tmdb_findyourmovie.ui.utils.rotateImage
import dev.skyit.tmdb_findyourmovie.ui.utils.snack
import java.io.File


@AndroidEntryPoint
class ProfileFragment : BaseFragment(R.layout.fragment_profile) {

    private val vModel: ProfileViewModel by viewModels()
    private val binding: FragmentProfileBinding by viewBinding()

    private lateinit var recentlyWatchedMoviesAdapter: SimpleRecyclerAdapter<MovieDb, ListItemRecentlyWatchedBinding>

    private fun buildRecentlyWatchedList() {
        recentlyWatchedMoviesAdapter = SimpleRecyclerAdapter({
            ListItemRecentlyWatchedBinding.inflate(it)
        }, { data ->
            this.moviePreview.transitionName = data.id.toString()
            this.moviePreview.load(data.posterPath)
            this.moviePreviewName.text = data.title
            this.simpleRatingBar.isVisible = false

            Coil.enqueue(
                ImageRequest.Builder(requireContext())
                    .data(data.backdropPath)
                    .build()
            )
        }, onItemClick = { v, item ->
            findNavController().navigate(
                ProfileFragmentDirections
                    .actionNavigationProfileToMovieDetailsFragment(
                        item, v.moviePreview.transitionName, item.id
                    ),
                FragmentNavigatorExtras(v.moviePreview to v.moviePreview.transitionName)
            )
        })

        binding.recentlyWatchedList.adapter = recentlyWatchedMoviesAdapter
        binding.recentlyWatchedList.layoutManager = GridLayoutManager(
            requireContext(),
            3,
            GridLayoutManager.VERTICAL,
            false
        )

        vModel.recentlyWatchedList.observe(viewLifecycleOwner, {
            recentlyWatchedMoviesAdapter.updateData(ArrayList(it))
        })


        vModel.loadRecentlyWatched()
    }

    private fun bindUI() {
        binding.signInBtn.setOnClickListener {
            findNavController().navigate(ProfileFragmentDirections.actionNavigationProfileToSignInFragment())
        }

        binding.signOutBtn.setOnClickListener {
            vModel.signOut()
            snack("Logged Out")
        }

        vModel.errorsLive.observe(viewLifecycleOwner, Observer {
            errAlert(it)
        })

        vModel.state.observe(viewLifecycleOwner, {
            val isAuth = it != null
            binding.signInBtn.isVisible = !isAuth
            binding.signOutBtn.isVisible = isAuth

            if (!isAuth) {
                binding.username.text = "Guest User"
                binding.userAvatar.setImageResource(R.drawable.profile_pic)
            } else {
                binding.username.text = it!!.username

                val path = it.profilePic
                if (path != null) {
                    binding.userAvatar.load(path)
                }

                binding.userAvatar.setOnClickListener {
                    pickImage()
//                    reqPermission()
                }
            }
        })
    }

    private fun reqPermission() {
        val requestPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                if (isGranted) {
                    // Permission is granted. Continue the action or workflow in your
                    // app.
                } else {
                    // Explain to the user that the feature is unavailable because the
                    // features requires a permission that the user has denied. At the
                    // same time, respect the user's decision. Don't link to system
                    // settings in an effort to convince the user to change their
                    // decision.
                }
            }

        when {
            ContextCompat.checkSelfPermission(
                this.context!!,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // You can use the API that requires the permission.
                pickImage()
            }
            else -> {
                // You can directly ask for the permission.
                // The registered ActivityResultCallback gets the result of this request.
                requestPermissionLauncher.launch(
                    Manifest.permission.CAMERA)
            }
        }
    }

    private fun pickImage() {
        ImagePicker.with(this)
            .compress(1024)
            .start { resultCode, data ->
                if (resultCode == Activity.RESULT_OK) {
                    val fileUri = data?.data ?: return@start

                    binding.userAvatar.setImageURI(fileUri)

                    val file: File? = ImagePicker.getFile(data)

                    file?.let {
                        val bitmap = BitmapFactory.decodeFile(it.path)
                        val ei = ExifInterface(it.path)
                        val orientation: Int = ei.getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_UNDEFINED
                        )
                        val adjustedBitmap = when(orientation) {
                            ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(bitmap, 90f)
                            ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(bitmap, 180f)
                            ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(bitmap, 270f)
                            else -> bitmap

                        }

                        vModel.uploadAvatar(adjustedBitmap)

                    }
                }
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        bindUI()

        buildRecentlyWatchedList()
    }
}